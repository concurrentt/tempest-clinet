// Tempest online service - Cloudflare Workers version (free plan).
//
// Same API as tempest-backend/server.js, so the mod needs no changes:
//   GET  /health                      -> { ok, service, online }
//   POST /v1/auth/start   {name}      -> { serverId }
//   POST /v1/auth/finish  {name, serverId, uuid, publicKey, expiresAt, keySignature, signature} -> { token, uuid, expiresIn }
//   POST /v1/heartbeat    (Bearer) {server?, public?, friends?} -> { ok, incoming }
//   POST /v1/lookup       {uuids}     -> { tempest: [...], cosmetics: {uuid: {...}} }
//   POST /v1/logout       (Bearer)    -> { ok }
//   POST /v1/friends/list    (Bearer)          -> { friends: [{uuid, online, server}], incoming: [uuid], outgoing: [uuid] }
//   POST /v1/cosmetics    (Bearer) {cape, wings, head, halo, wingSize} -> { ok }   (shown to other Tempest players)
//   GET  /v1/live         (Bearer, WebSocket) live channel:
//        client -> {"t":"watch","u":"<uuid hex><uuid hex>..."}  players near me (up to 48)
//        client -> {"t":"emote","id":"wave"|null}              play / stop an emote
//        server -> {"t":"emote","uuid":"...","id":"wave"|null}  a watched player emoted
//        server -> {"t":"cos","uuid":"...","c":{...}}           a watched player changed cosmetics
//        "ping" -> "pong" (answered without waking anything)
//   POST /v1/friends/request (Bearer) {uuid}   -> { result: "sent" | "accepted" | "already" }
//   POST /v1/friends/accept | decline | remove (Bearer) {uuid} -> { ok }
//
// Friendships are stored by account UUID only (the UUID is proven at sign-in; names are not), and the game shows
// names it looks up from Mojang itself, so nobody can pose as someone else in a friend request.
//
// How it fits Cloudflare's free plan:
// - Proving who you are: every real Minecraft account has a key pair issued by Mojang (the one that signs chat).
//   The game signs our one-time code with it and sends the public half plus Mojang's certificate for it. We check
//   Mojang's certificate with Mojang's published keys, then the signature. Mojang's servers block Cloudflare, so
//   nothing here calls them during sign-in, and your Minecraft access token is never used or sent anywhere.
// - Sign-in needs no storage: the one-time code and the login token are signed with TOKEN_SECRET (HMAC).
// - "Who is online" lives in memory inside ONE Durable Object (Presence). No database writes at all, so the
//   free plan's write limits never come into play. If Cloudflare restarts it, the list refills from heartbeats
//   within a minute.

import { DurableObject } from "cloudflare:workers";
import BUNDLED_KEYS from "./mojang-keys.json";

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

/** One-time code: 8 hex expiry (seconds) + 8 hex random + 24 hex signature = 40 hex chars. */
async function makeChallenge(secret, name, now) {
  const exp = Math.floor((now + CHALLENGE_TTL_MS) / 1000).toString(16).padStart(8, "0");
  const nonce = hex(crypto.getRandomValues(new Uint8Array(4)));
  return exp + nonce + hex(await mac(secret, `challenge|${name}|${exp}|${nonce}`)).slice(0, 24);
}

async function checkChallenge(secret, name, serverId, now) {
  if (typeof serverId !== "string" || !/^[0-9a-f]{40}$/.test(serverId)) return false;
  const exp = serverId.slice(0, 8), nonce = serverId.slice(8, 16);
  if (parseInt(exp, 16) * 1000 < now) return false;
  return same(serverId.slice(16), hex(await mac(secret, `challenge|${name}|${exp}|${nonce}`)).slice(0, 24));
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

// ------------------------------------------------------------------ Mojang player certificates
const b64bytes = (s) => Uint8Array.from(atob(String(s).replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));

let mojangKeys = null, mojangKeysAt = 0;

/** Mojang's player-certificate keys (CryptoKeys): bundled list, refreshed from Mojang once a day when reachable. */
async function certificateKeys(override) {
  if (override) return override;
  const now = Date.now();
  if (mojangKeys && now - mojangKeysAt < 24 * 3600_000) return mojangKeys;
  let list = (BUNDLED_KEYS && BUNDLED_KEYS.playerCertificateKeys) || [];
  try {
    const res = await fetch("https://api.minecraftservices.com/publickeys", {
      headers: { "User-Agent": "TempestClient-Auth/1.0" }, signal: AbortSignal.timeout(5000),
    });
    if (res.ok) {
      const j = await res.json();
      if (Array.isArray(j.playerCertificateKeys) && j.playerCertificateKeys.length) list = j.playerCertificateKeys;
    }
  } catch { /* keep the bundled list */ }
  const keys = [];
  for (const k of list) {
    try {
      keys.push(await crypto.subtle.importKey("spki", b64bytes(k.publicKey), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-1" }, false, ["verify"]));
    } catch { /* skip a bad entry */ }
  }
  mojangKeys = keys;
  mojangKeysAt = now;
  return keys;
}

/** Bytes Mojang signs for a player key (same as Minecraft's PublicKeyData): uuid (16) + expiry ms (8) + key DER. */
function certificatePayload(uuid, expiresAt, keyDer) {
  const out = new Uint8Array(24 + keyDer.length);
  const hexs = uuid.replace(/-/g, "");
  for (let i = 0; i < 16; i++) out[i] = parseInt(hexs.slice(i * 2, i * 2 + 2), 16);
  new DataView(out.buffer).setBigUint64(16, BigInt(expiresAt));
  out.set(keyDer, 24);
  return out;
}

/**
 * Checks a sign-in proof. Returns the verified uuid, or { why } if it fails.
 * body: { uuid, publicKey (base64 DER), expiresAt (ms), keySignature (base64), signature (base64) }
 */
export async function verifyProof(body, serverId, now, keysOverride) {
  const uuid = normUuid(body.uuid);
  if (!uuid) return { why: "bad uuid" };
  const expiresAt = Number(body.expiresAt);
  if (!Number.isSafeInteger(expiresAt) || expiresAt < now) return { why: "player key expired" };
  let keyDer, keySig, sig;
  try {
    keyDer = b64bytes(body.publicKey);
    keySig = b64bytes(body.keySignature);
    sig = b64bytes(body.signature);
  } catch { return { why: "bad base64" }; }
  if (keyDer.length > 1024 || keySig.length > 1024 || sig.length > 1024) return { why: "too large" };

  // 1) Mojang issued this key to this player
  const payload = certificatePayload(uuid, expiresAt, keyDer);
  let certified = false;
  for (const k of await certificateKeys(keysOverride)) {
    if (await crypto.subtle.verify("RSASSA-PKCS1-v1_5", k, keySig, payload)) { certified = true; break; }
  }
  if (!certified) return { why: "player key not signed by Mojang" };

  // 2) the player signed our one-time code with it
  let playerKey;
  try {
    playerKey = await crypto.subtle.importKey("spki", keyDer, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  } catch { return { why: "bad player key" }; }
  const msg = enc.encode(`tempest-auth|${serverId}|${uuid.replace(/-/g, "")}`);
  if (!(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", playerKey, sig, msg))) return { why: "bad signature" };
  return uuid;
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
const MAX_FRIENDS = 200;
// Who's online is also saved (at most every 4 minutes per player, or when their status changes), so a restart of
// this object (Cloudflare evicts idle objects and every deploy restarts it) doesn't make everyone look offline.
const PERSIST_EVERY_MS = 240_000;
const MAX_OUTGOING = 30;
const MAX_INCOMING = 100;

/** Only known-shaped cosmetic choices are stored: short names and a wing size. */
function cleanCosmetics(c) {
  const out = {};
  if (!c || typeof c !== "object") return out;
  for (const k of ["cape", "wings", "head", "halo"]) {
    if (typeof c[k] === "string" && /^[A-Za-z ]{1,24}$/.test(c[k])) out[k] = c[k];
  }
  const ws = Number(c.wingSize);
  if (Number.isFinite(ws)) out.wingSize = Math.round(Math.min(1.6, Math.max(0.6, ws)) * 100) / 100;
  return out;
}

/** Cleans the server address a player reports (shown to their friends only). */
function cleanServer(s) {
  if (typeof s !== "string") return null;
  const t = s.replace(/[^\x20-\x7E]/g, "").trim().slice(0, 64);
  return t || null;
}

// One shared Durable Object: who is online (memory) + friendships and requests (its built-in SQLite database).
export class Presence extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.online = new Map(); // uuid -> { seen, server, pub, fr }
    this.sql = ctx.storage.sql;
    this.sql.exec("CREATE TABLE IF NOT EXISTS friends (a TEXT NOT NULL, b TEXT NOT NULL, since INTEGER NOT NULL, PRIMARY KEY (a, b))");
    this.sql.exec("CREATE TABLE IF NOT EXISTS requests (src TEXT NOT NULL, dst TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (src, dst))");
    this.sql.exec("CREATE INDEX IF NOT EXISTS requests_dst ON requests (dst)");
    this.sql.exec("CREATE TABLE IF NOT EXISTS presence (uuid TEXT PRIMARY KEY, seen INTEGER NOT NULL, server TEXT, pub INTEGER NOT NULL, fr INTEGER NOT NULL)");
    this.sql.exec("CREATE TABLE IF NOT EXISTS cosmetics (uuid TEXT PRIMARY KEY, data TEXT NOT NULL, at INTEGER NOT NULL)");
    this.msgRate = new Map();   // socket rate limiting (memory only)
    // "ping" is answered by Cloudflare itself, so keep-alives never wake this object or count as requests
    if (typeof WebSocketRequestResponsePair !== "undefined" && ctx.setWebSocketAutoResponse) {
      ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
    }
    this.persisted = new Map(); // uuid -> { at, key } last saved
    // restore players who were online before a restart; give them until their next heartbeat to check in
    const now = Date.now();
    this.sql.exec("DELETE FROM presence WHERE seen < ?", now - 3600_000);
    for (const r of this.rows("SELECT uuid, seen, server, pub, fr FROM presence WHERE seen > ?", now - ONLINE_TTL_MS - PERSIST_EVERY_MS)) {
      const seen = Math.min(now, Number(r.seen) + PERSIST_EVERY_MS);
      this.online.set(r.uuid, { seen, server: r.server ?? null, pub: !!r.pub, fr: !!r.fr });
      this.persisted.set(r.uuid, { at: Number(r.seen), key: `${r.server}|${r.pub}|${r.fr}` });
    }
  }

  rows(q, ...args) { return this.sql.exec(q, ...args).toArray(); }
  num(q, ...args) { const r = this.rows(q, ...args); return r.length ? Number(Object.values(r[0])[0]) : 0; }

  prune(now) {
    for (const [k, v] of this.online) if (now - v.seen > ONLINE_TTL_MS) this.online.delete(k);
  }

  /** opts: { server, pub (show logo to everyone), fr (show online to friends) }. Returns pending incoming count. */
  heartbeat(uuid, opts = {}) {
    const pub = opts.pub !== false, fr = opts.fr !== false;
    if (!pub && !fr) this.logout(uuid);
    else {
      const now = Date.now(), server = cleanServer(opts.server);
      this.online.set(uuid, { seen: now, server, pub, fr });
      const key = `${server}|${pub ? 1 : 0}|${fr ? 1 : 0}`, last = this.persisted.get(uuid);
      if (!last || last.key !== key || now - last.at >= PERSIST_EVERY_MS) {
        this.sql.exec("INSERT OR REPLACE INTO presence (uuid, seen, server, pub, fr) VALUES (?, ?, ?, ?, ?)",
          uuid, now, server, pub ? 1 : 0, fr ? 1 : 0);
        this.persisted.set(uuid, { at: now, key });
      }
    }
    return this.num("SELECT COUNT(*) AS n FROM requests WHERE dst = ?", uuid);
  }

  logout(uuid) {
    this.online.delete(uuid);
    if (this.persisted.delete(uuid)) this.sql.exec("DELETE FROM presence WHERE uuid = ?", uuid);
  }

  lookup(uuids) {
    const now = Date.now();
    this.prune(now);
    return uuids.filter((u) => { const o = this.online.get(u); return o && o.pub; });
  }

  /** Online Tempest users among uuids, plus their cosmetics. */
  lookupFull(uuids) {
    const tempest = this.lookup(uuids);
    const cosmetics = {};
    for (let i = 0; i < tempest.length; i += 50) {
      const part = tempest.slice(i, i + 50);
      const rows = this.rows(`SELECT uuid, data FROM cosmetics WHERE uuid IN (${part.map(() => "?").join(",")})`, ...part);
      for (const r of rows) { try { cosmetics[r.uuid] = JSON.parse(r.data); } catch { /* skip */ } }
    }
    return { tempest, cosmetics };
  }

  /** Saves what someone wears and tells players currently near them. */
  setCosmetics(uuid, c) {
    const clean = cleanCosmetics(c);
    this.sql.exec("INSERT OR REPLACE INTO cosmetics (uuid, data, at) VALUES (?, ?, ?)", uuid, JSON.stringify(clean), Date.now());
    this.broadcast(uuid, { t: "cos", uuid, c: clean });
    return clean;
  }

  // ---------------------------------------------------------------- live channel (WebSocket, hibernating)
  async fetch(request) {
    const uuid = request.headers.get("X-Tempest-User");
    if (!uuid || request.headers.get("Upgrade") !== "websocket") return new Response("expected websocket", { status: 426 });
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server, [uuid]);
    server.serializeAttachment({ u: uuid, w: "" });
    return new Response(null, { status: 101, webSocket: client });
  }

  /** Sends msg to every connection that is watching `uuid` (i.e. has that player near them). */
  broadcast(uuid, msg) {
    const hex = uuid.replace(/-/g, "");
    const text = JSON.stringify(msg);
    for (const ws of this.ctx.getWebSockets ? this.ctx.getWebSockets() : []) {
      const a = ws.deserializeAttachment();
      if (!a || a.u === uuid || !a.w) continue;
      for (let i = 0; i < a.w.length; i += 32) {
        if (a.w.startsWith(hex, i)) { try { ws.send(text); } catch { /* closed */ } break; }
      }
    }
  }

  webSocketMessage(ws, message) {
    const a = ws.deserializeAttachment();
    if (!a || typeof message !== "string" || message.length > 4096) return;
    // at most 30 messages per 10 s per connection
    const now = Date.now(), r = this.msgRate.get(a.u) || { n: 0, at: now };
    if (now - r.at > 10_000) { r.n = 0; r.at = now; }
    if (++r.n > 30) { this.msgRate.set(a.u, r); return; }
    this.msgRate.set(a.u, r);
    let m;
    try { m = JSON.parse(message); } catch { return; }
    if (m.t === "watch" && typeof m.u === "string") {
      const w = m.u.toLowerCase().replace(/[^0-9a-f]/g, "").slice(0, 48 * 32);
      ws.serializeAttachment({ u: a.u, w: w.slice(0, w.length - (w.length % 32)) });
    } else if (m.t === "emote") {
      const id = typeof m.id === "string" && /^[a-z_]{1,16}$/.test(m.id) ? m.id : null;
      this.broadcast(a.u, { t: "emote", uuid: a.u, id });
    }
  }

  webSocketClose(ws, code) {
    try { ws.close(code, "bye"); } catch { /* already closed */ }
  }

  count() {
    this.prune(Date.now());
    return this.online.size;
  }

  friendsState(me) {
    this.prune(Date.now());
    const friends = this.rows("SELECT b FROM friends WHERE a = ? ORDER BY since", me).map((r) => {
      const o = this.online.get(r.b);
      const on = !!(o && o.fr);
      return { uuid: r.b, online: on, server: on ? o.server : null };
    });
    const incoming = this.rows("SELECT src FROM requests WHERE dst = ? ORDER BY at DESC", me).map((r) => r.src);
    const outgoing = this.rows("SELECT dst FROM requests WHERE src = ? ORDER BY at DESC", me).map((r) => r.dst);
    return { friends, incoming, outgoing };
  }

  isFriend(a, b) { return this.num("SELECT COUNT(*) AS n FROM friends WHERE a = ? AND b = ?", a, b) > 0; }

  link(a, b) {
    const now = Date.now();
    this.sql.exec("INSERT OR IGNORE INTO friends (a, b, since) VALUES (?, ?, ?)", a, b, now);
    this.sql.exec("INSERT OR IGNORE INTO friends (a, b, since) VALUES (?, ?, ?)", b, a, now);
    this.sql.exec("DELETE FROM requests WHERE (src = ? AND dst = ?) OR (src = ? AND dst = ?)", a, b, b, a);
  }

  request(me, other) {
    if (me === other) return { error: "you can't add yourself" };
    if (this.isFriend(me, other)) return { result: "already" };
    // they already asked you: that's a yes from both sides
    if (this.num("SELECT COUNT(*) AS n FROM requests WHERE src = ? AND dst = ?", other, me) > 0) {
      if (this.num("SELECT COUNT(*) AS n FROM friends WHERE a = ?", me) >= MAX_FRIENDS) return { error: "your friend list is full" };
      if (this.num("SELECT COUNT(*) AS n FROM friends WHERE a = ?", other) >= MAX_FRIENDS) return { error: "their friend list is full" };
      this.link(me, other);
      return { result: "accepted" };
    }
    if (this.num("SELECT COUNT(*) AS n FROM requests WHERE src = ? AND dst = ?", me, other) > 0) return { result: "sent" };
    if (this.num("SELECT COUNT(*) AS n FROM requests WHERE src = ?", me) >= MAX_OUTGOING) return { error: "too many pending requests - cancel some first" };
    if (this.num("SELECT COUNT(*) AS n FROM requests WHERE dst = ?", other) >= MAX_INCOMING) return { error: "they have too many pending requests" };
    this.sql.exec("INSERT INTO requests (src, dst, at) VALUES (?, ?, ?)", me, other, Date.now());
    return { result: "sent" };
  }

  accept(me, other) {
    if (this.num("SELECT COUNT(*) AS n FROM requests WHERE src = ? AND dst = ?", other, me) === 0) return { error: "no request from that player" };
    if (this.num("SELECT COUNT(*) AS n FROM friends WHERE a = ?", me) >= MAX_FRIENDS) return { error: "your friend list is full" };
    if (this.num("SELECT COUNT(*) AS n FROM friends WHERE a = ?", other) >= MAX_FRIENDS) return { error: "their friend list is full" };
    this.link(me, other);
    return { ok: true };
  }

  /** Declines an incoming request or cancels an outgoing one. */
  decline(me, other) {
    this.sql.exec("DELETE FROM requests WHERE (src = ? AND dst = ?) OR (src = ? AND dst = ?)", me, other, other, me);
    return { ok: true };
  }

  remove(me, other) {
    this.sql.exec("DELETE FROM friends WHERE (a = ? AND b = ?) OR (a = ? AND b = ?)", me, other, other, me);
    return { ok: true };
  }
}

function presence(env) {
  return env.PRESENCE.get(env.PRESENCE.idFromName("global"));
}

// ------------------------------------------------------------------ routes
export async function handle(req, env, keysOverride = null) {
  const url = new URL(req.url);
  const path = url.pathname;
  const now = Date.now();
  const secret = env.TOKEN_SECRET;
  if (!secret || secret.length < 32) return json(500, { error: "server not configured (TOKEN_SECRET)" });

  if (req.method === "GET" && (path === "/" || path === "/health")) {
    return json(200, { ok: true, service: "tempest", online: await presence(env).count() });
  }

  if (rateLimited(req.headers.get("CF-Connecting-IP") || "?", now)) return json(429, { error: "slow down" });

  if (path === "/v1/live") {
    if (req.headers.get("Upgrade") !== "websocket") return json(426, { error: "expected websocket" });
    const m0 = /^Bearer (\S+)$/.exec(req.headers.get("Authorization") || "");
    const who = m0 ? await checkToken(secret, m0[1], now) : null;
    if (!who) return json(401, { error: "sign in first" });
    // the user id comes only from the checked token, never from the client's own headers
    const headers = new Headers({ "Upgrade": "websocket", "X-Tempest-User": who });
    return presence(env).fetch(new Request("https://presence/live", { headers }));
  }

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
    const r = await verifyProof(body, serverId, now, keysOverride);
    if (typeof r !== "string") {
      console.log(`auth/finish: refused ${name}: ${r.why}`);
      return json(403, { error: "could not verify account", why: r.why });
    }
    const uuid = r;
    return json(200, { token: await makeToken(secret, uuid, now), uuid, expiresIn: TOKEN_TTL_MS / 1000 });
  }

  const m = /^Bearer (\S+)$/.exec(req.headers.get("Authorization") || "");
  const me = m ? await checkToken(secret, m[1], now) : null;
  if (!me) return json(401, { error: "sign in first" });

  if (path === "/v1/heartbeat") {
    const incoming = await presence(env).heartbeat(me, { server: body.server, pub: body.public, fr: body.friends });
    return json(200, { ok: true, incoming, visibleToFriends: body.friends !== false });
  }

  if (path === "/v1/friends/list") return json(200, await presence(env).friendsState(me));

  if (path === "/v1/cosmetics") return json(200, { ok: true, c: await presence(env).setCosmetics(me, body) });

  if (path.startsWith("/v1/friends/")) {
    const other = normUuid(body.uuid);
    if (!other) return json(400, { error: "bad uuid" });
    const p = presence(env);
    let r;
    if (path === "/v1/friends/request") r = await p.request(me, other);
    else if (path === "/v1/friends/accept") r = await p.accept(me, other);
    else if (path === "/v1/friends/decline") r = await p.decline(me, other);
    else if (path === "/v1/friends/remove") r = await p.remove(me, other);
    else return json(404, { error: "not found" });
    return json(r.error ? 400 : 200, r);
  }

  if (path === "/v1/lookup") {
    if (!Array.isArray(body.uuids)) return json(400, { error: "uuids must be a list" });
    if (body.uuids.length > MAX_LOOKUP) return json(400, { error: `at most ${MAX_LOOKUP} uuids` });
    const list = [...new Set(body.uuids.map(normUuid).filter(Boolean))];
    return json(200, list.length ? await presence(env).lookupFull(list) : { tempest: [], cosmetics: {} });
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
