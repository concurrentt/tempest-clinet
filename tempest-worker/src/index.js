// Tempest online service - Cloudflare Workers version (free plan).
//
// Same API as tempest-backend/server.js, so the mod needs no changes:
//   GET  /health                      -> { ok, service, online }
//   POST /v1/auth/start   {name}      -> { serverId }
//   POST /v1/auth/finish  {name, serverId} -> { token, uuid, expiresIn }
//   POST /v1/heartbeat    (Bearer)    -> { ok }
//   POST /v1/lookup       {uuids}     -> { tempest: [...] }
//   POST /v1/logout       (Bearer)    -> { ok }
//
// How it fits Cloudflare's free plan:
// - Sign-in needs no storage: the one-time code and the login token are signed with TOKEN_SECRET (HMAC), so any
//   Worker anywhere can check them. Your Minecraft access token still only ever goes to Mojang.
// - "Who is online" lives in memory inside ONE Durable Object (Presence). No database writes at all, so the
//   free plan's write limits never come into play. If Cloudflare restarts it, the list refills from heartbeats
//   within a minute.

import { DurableObject } from "cloudflare:workers";

const ONLINE_TTL_MS = 150_000;       // online for 2.5 min after the last heartbeat
const TOKEN_TTL_MS = 24 * 3600_000;  // sign in again once a day
const CHALLENGE_TTL_MS = 60_000;     // time to finish the Mojang step
const MAX_LOOKUP = 200;
const MAX_BODY = 16 * 1024;
const RATE_PER_MIN = 120;

const NAME_RE = /^[A-Za-z0-9_]{1,16}$/;
const UUID_RE = /^[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}$/i;

export function normUuid(s) {
  if (typeof s !== "string" || !UUID_RE.test(s)) return null;
  const h = s.replace(/-/g, "").toLowerCase();
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
  });
}

// ------------------------------------------------------------------ signing helpers
const enc = new TextEncoder();
const keyCache = new Map();

async function hmacKey(secret) {
  let k = keyCache.get(secret);
  if (!k) {
    k = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
    keyCache.set(secret, k);
  }
  return k;
}

async function mac(secret, text) {
  const sig = await crypto.subtle.sign("HMAC", await hmacKey(secret), enc.encode(text));
  return new Uint8Array(sig);
}

const hex = (bytes) => [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
const b64url = (bytes) => btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

/** Constant-time string compare. */
function same(a, b) {
  if (a.length !== b.length) return false;
  let d = 0;
  for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return d === 0;
}

/** One-time code for the Mojang step: 8 hex chars of expiry (seconds) + 32 hex chars of signature. */
async function makeChallenge(secret, name, now) {
  const exp = Math.floor((now + CHALLENGE_TTL_MS) / 1000).toString(16).padStart(8, "0");
  return exp + hex(await mac(secret, `challenge|${name}|${exp}`)).slice(0, 32);
}

async function checkChallenge(secret, name, serverId, now) {
  if (typeof serverId !== "string" || !/^[0-9a-f]{40}$/.test(serverId)) return false;
  const exp = serverId.slice(0, 8);
  if (parseInt(exp, 16) * 1000 < now) return false;
  return same(serverId.slice(8), hex(await mac(secret, `challenge|${name}|${exp}`)).slice(0, 32));
}

/** Login token: "<uuid without dashes><expiry ms, base 36>_<signature>" - URL-safe, no storage needed. */
async function makeToken(secret, uuid, now) {
  const payload = uuid.replace(/-/g, "") + (now + TOKEN_TTL_MS).toString(36);
  return payload + "_" + b64url(await mac(secret, `token|${payload}`));
}

async function checkToken(secret, token, now) {
  const m = /^([0-9a-f]{32})([0-9a-z]{1,12})_([A-Za-z0-9_-]{43})$/.exec(token || "");
  if (!m) return null;
  if (parseInt(m[2], 36) < now) return null;
  if (!same(m[3], b64url(await mac(secret, `token|${m[1]}${m[2]}`)))) return null;
  return normUuid(m[1]);
}

// ------------------------------------------------------------------ Mojang
async function mojangHasJoined(name, serverId) {
  const url = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
    + encodeURIComponent(name) + "&serverId=" + encodeURIComponent(serverId);
  const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
  if (res.status !== 200) return null; // 204 = did not join
  const body = await res.json();
  return normUuid(body && body.id);
}

// ------------------------------------------------------------------ rate limit (best effort, per Worker instance)
const rate = new Map();
function rateLimited(ip, now) {
  const r = rate.get(ip);
  if (!r || now - r.start > 60_000) {
    if (rate.size > 5000) rate.clear();
    rate.set(ip, { count: 1, start: now });
    return false;
  }
  return ++r.count > RATE_PER_MIN;
}

async function readJson(req) {
  const text = await req.text();
  if (text.length > MAX_BODY) throw new Error("too large");
  if (!text) return {};
  try { return JSON.parse(text); } catch { throw new Error("bad json"); }
}

// ------------------------------------------------------------------ who is online (one shared Durable Object)
export class Presence extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.online = new Map(); // uuid -> last heartbeat (ms)
  }

  prune(now) {
    for (const [k, v] of this.online) if (now - v > ONLINE_TTL_MS) this.online.delete(k);
  }

  heartbeat(uuid) { this.online.set(uuid, Date.now()); }

  logout(uuid) { this.online.delete(uuid); }

  lookup(uuids) {
    const now = Date.now();
    this.prune(now);
    return uuids.filter((u) => this.online.has(u));
  }

  count() {
    this.prune(Date.now());
    return this.online.size;
  }
}

function presence(env) {
  return env.PRESENCE.get(env.PRESENCE.idFromName("global"));
}

// ------------------------------------------------------------------ routes
export async function handle(req, env, hasJoined = mojangHasJoined) {
  const url = new URL(req.url);
  const path = url.pathname;
  const now = Date.now();
  const secret = env.TOKEN_SECRET;
  if (!secret || secret.length < 32) return json(500, { error: "server not configured (TOKEN_SECRET)" });

  if (req.method === "GET" && (path === "/" || path === "/health")) {
    return json(200, { ok: true, service: "tempest", online: await presence(env).count() });
  }

  if (rateLimited(req.headers.get("CF-Connecting-IP") || "?", now)) return json(429, { error: "slow down" });
  if (req.method !== "POST") return json(404, { error: "not found" });

  let body;
  try { body = await readJson(req); } catch (e) { return json(400, { error: e.message }); }

  if (path === "/v1/auth/start") {
    const name = body.name;
    if (typeof name !== "string" || !NAME_RE.test(name)) return json(400, { error: "bad name" });
    return json(200, { serverId: await makeChallenge(secret, name, now) });
  }

  if (path === "/v1/auth/finish") {
    const { name, serverId } = body;
    if (typeof name !== "string" || !NAME_RE.test(name) || !(await checkChallenge(secret, name, serverId, now))) {
      return json(400, { error: "bad or expired code" });
    }
    let uuid;
    try { uuid = await hasJoined(name, serverId); } catch { return json(502, { error: "mojang unavailable" }); }
    if (!uuid) return json(403, { error: "could not verify account" });
    await presence(env).heartbeat(uuid);
    return json(200, { token: await makeToken(secret, uuid, now), uuid, expiresIn: TOKEN_TTL_MS / 1000 });
  }

  const m = /^Bearer (\S+)$/.exec(req.headers.get("Authorization") || "");
  const me = m ? await checkToken(secret, m[1], now) : null;
  if (!me) return json(401, { error: "sign in first" });

  if (path === "/v1/heartbeat") {
    await presence(env).heartbeat(me);
    return json(200, { ok: true });
  }

  if (path === "/v1/lookup") {
    if (!Array.isArray(body.uuids)) return json(400, { error: "uuids must be a list" });
    if (body.uuids.length > MAX_LOOKUP) return json(400, { error: `at most ${MAX_LOOKUP} uuids` });
    const list = [...new Set(body.uuids.map(normUuid).filter(Boolean))];
    return json(200, { tempest: list.length ? await presence(env).lookup(list) : [] });
  }

  if (path === "/v1/logout") {
    await presence(env).logout(me);
    return json(200, { ok: true });
  }

  return json(404, { error: "not found" });
}

export default {
  async fetch(req, env) {
    try {
      return await handle(req, env);
    } catch (e) {
      console.error("request failed:", e);
      return json(500, { error: "server error" });
    }
  },
};
