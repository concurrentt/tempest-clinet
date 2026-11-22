// Tempest online service.
//
// Tempest clients sign in once with Mojang's session check (the same check Minecraft servers use), then send a
// heartbeat every minute while they play. Clients ask "which of these players use Tempest?" so they can show the
// Tempest logo on those nametags. Nothing is stored on disk: the list of who is online lives in memory and is
// rebuilt from heartbeats if the service restarts.
//
// No dependencies. Needs Node 18+ (built-in fetch). Railway sets PORT for you.

const http = require("http");
const crypto = require("crypto");

const PORT = Number(process.env.PORT) || 8080;
// Local testing only: skips the Mojang check and trusts the name the client sends. NEVER set this on Railway.
const DEV_SKIP_MOJANG = process.env.TEMPEST_DEV_SKIP_MOJANG === "1";

const ONLINE_TTL_MS = 150_000;        // a player counts as online for 2.5 min after their last heartbeat
const TOKEN_TTL_MS = 24 * 3600_000;   // sign in again once a day
const CHALLENGE_TTL_MS = 60_000;      // time to finish the Mojang step
const MAX_LOOKUP = 200;               // players per lookup request
const MAX_BODY = 16 * 1024;           // bytes
const RATE_PER_MIN = 120;             // requests per minute per IP

const challenges = new Map(); // serverId -> { name, expires }
const tokens = new Map();     // token -> { uuid, expires }
const online = new Map();     // uuid -> lastSeen (ms)
const rate = new Map();       // ip -> { count, windowStart }

const NAME_RE = /^[A-Za-z0-9_]{1,16}$/;
const UUID_RE = /^[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}$/i;

/** Lower-case, dashed UUID, or null if it isn't one. */
function normUuid(s) {
  if (typeof s !== "string" || !UUID_RE.test(s)) return null;
  const h = s.replace(/-/g, "").toLowerCase();
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

function send(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json", "Cache-Control": "no-store" });
  res.end(data);
}

function clientIp(req) {
  // Railway sits behind a proxy and passes the real address in x-forwarded-for.
  const fwd = req.headers["x-forwarded-for"];
  return (typeof fwd === "string" && fwd.split(",")[0].trim()) || req.socket.remoteAddress || "?";
}

function rateLimited(ip) {
  const now = Date.now();
  const r = rate.get(ip);
  if (!r || now - r.windowStart > 60_000) {
    rate.set(ip, { count: 1, windowStart: now });
    return false;
  }
  r.count++;
  return r.count > RATE_PER_MIN;
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on("data", (c) => {
      size += c.length;
      if (size > MAX_BODY) {
        reject(new Error("too large"));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on("end", () => {
      if (chunks.length === 0) return resolve({});
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString("utf8")));
      } catch {
        reject(new Error("bad json"));
      }
    });
    req.on("error", reject);
  });
}

/** The signed-in player's UUID for this request, or null. */
function auth(req) {
  const h = req.headers["authorization"] || "";
  const m = /^Bearer ([A-Za-z0-9_-]{20,})$/.exec(h);
  if (!m) return null;
  const t = tokens.get(m[1]);
  if (!t || t.expires < Date.now()) return null;
  return t.uuid;
}

/** Asks Mojang whether `name` really joined with `serverId`. Returns the player's UUID, or null. */
async function mojangHasJoined(name, serverId) {
  if (DEV_SKIP_MOJANG) {
    // Fake but stable UUID from the name, for local tests only.
    const h = crypto.createHash("md5").update("tempest-dev:" + name).digest("hex");
    return normUuid(h);
  }
  const url = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
    + encodeURIComponent(name) + "&serverId=" + encodeURIComponent(serverId);
  const res = await fetch(url, { signal: AbortSignal.timeout(8000) });
  if (res.status !== 200) return null;   // 204 = did not join
  const body = await res.json();
  return normUuid(body && body.id);
}

async function handle(req, res) {
  const url = new URL(req.url, "http://localhost");
  const path = url.pathname;

  if (req.method === "GET" && (path === "/" || path === "/health")) {
    return send(res, 200, { ok: true, service: "tempest", online: online.size });
  }

  if (rateLimited(clientIp(req))) return send(res, 429, { error: "slow down" });
  if (req.method !== "POST") return send(res, 404, { error: "not found" });

  let body;
  try {
    body = await readJson(req);
  } catch (e) {
    return send(res, 400, { error: e.message });
  }

  // 1) Start sign-in: hand out a one-time code for the Mojang step.
  if (path === "/v1/auth/start") {
    const name = body.name;
    if (typeof name !== "string" || !NAME_RE.test(name)) return send(res, 400, { error: "bad name" });
    const serverId = crypto.randomBytes(10).toString("hex");
    challenges.set(serverId, { name, expires: Date.now() + CHALLENGE_TTL_MS });
    return send(res, 200, { serverId });
  }

  // 2) Finish sign-in: the client has "joined" with the code through Mojang; check it and issue a token.
  if (path === "/v1/auth/finish") {
    const { name, serverId } = body;
    const ch = typeof serverId === "string" ? challenges.get(serverId) : null;
    if (!ch || ch.expires < Date.now() || ch.name !== name) return send(res, 400, { error: "bad or expired code" });
    challenges.delete(serverId);
    let uuid;
    try {
      uuid = await mojangHasJoined(name, serverId);
    } catch {
      return send(res, 502, { error: "mojang unavailable" });
    }
    if (!uuid) return send(res, 403, { error: "could not verify account" });
    const token = crypto.randomBytes(24).toString("base64url");
    tokens.set(token, { uuid, expires: Date.now() + TOKEN_TTL_MS });
    online.set(uuid, Date.now());
    return send(res, 200, { token, uuid, expiresIn: TOKEN_TTL_MS / 1000 });
  }

  const me = auth(req);
  if (!me) return send(res, 401, { error: "sign in first" });

  // 3) Heartbeat: "I'm online with Tempest".
  if (path === "/v1/heartbeat") {
    online.set(me, Date.now());
    return send(res, 200, { ok: true });
  }

  // 4) Lookup: which of these players use Tempest right now?
  if (path === "/v1/lookup") {
    if (!Array.isArray(body.uuids)) return send(res, 400, { error: "uuids must be a list" });
    if (body.uuids.length > MAX_LOOKUP) return send(res, 400, { error: `at most ${MAX_LOOKUP} uuids` });
    const now = Date.now();
    const tempest = [];
    for (const raw of body.uuids) {
      const u = normUuid(raw);
      if (!u) continue;
      const seen = online.get(u);
      if (seen && now - seen < ONLINE_TTL_MS) tempest.push(u);
    }
    return send(res, 200, { tempest });
  }

  // 5) Sign out: stop showing as online straight away.
  if (path === "/v1/logout") {
    online.delete(me);
    return send(res, 200, { ok: true });
  }

  return send(res, 404, { error: "not found" });
}

// Drop expired entries once a minute so memory stays flat.
setInterval(() => {
  const now = Date.now();
  for (const [k, v] of challenges) if (v.expires < now) challenges.delete(k);
  for (const [k, v] of tokens) if (v.expires < now) tokens.delete(k);
  for (const [k, v] of online) if (now - v > ONLINE_TTL_MS) online.delete(k);
  for (const [k, v] of rate) if (now - v.windowStart > 120_000) rate.delete(k);
}, 60_000).unref();

const server = http.createServer((req, res) => {
  handle(req, res).catch((e) => {
    console.error("request failed:", e);
    if (!res.headersSent) send(res, 500, { error: "server error" });
  });
});

server.listen(PORT, () => {
  console.log(`Tempest service listening on port ${PORT}`);
  if (DEV_SKIP_MOJANG) console.warn("WARNING: TEMPEST_DEV_SKIP_MOJANG=1 - accounts are NOT verified. Local testing only.");
});

module.exports = { server, normUuid };
