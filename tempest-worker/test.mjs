// Local test of the Worker logic (no Cloudflare needed): node test.mjs
import { readFileSync, writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import assert from "node:assert/strict";

const src = readFileSync(new URL("./src/index.js", import.meta.url), "utf8")
  .replace('import { DurableObject } from "cloudflare:workers";', "class DurableObject { constructor(ctx, env) { this.ctx = ctx; this.env = env; } }")
  .replace('import BUNDLED_KEYS from "./mojang-keys.json";', "const BUNDLED_KEYS = { playerCertificateKeys: [] };");
const dir = mkdtempSync(join(tmpdir(), "tw-"));
writeFileSync(join(dir, "w.mjs"), src);
const w = await import(pathToFileURL(join(dir, "w.mjs")).href);
const subtle = crypto.subtle;
const b64 = (buf) => Buffer.from(buf).toString("base64");

// fake "Mojang" (SHA1withRSA, like the real player-certificate keys) and a player key pair (signs with SHA256withRSA)
const gen = (hash) => subtle.generateKey({ name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash }, true, ["sign", "verify"]);
const mojang = await gen("SHA-1"), player = await gen("SHA-256"), stranger = await gen("SHA-256");

// the Durable Object's SQLite, played by Node's built-in SQLite
const { DatabaseSync } = await import("node:sqlite");
const db = new DatabaseSync(":memory:");
const sql = { exec: (q, ...args) => {
  const st = db.prepare(q);
  const rows = /^\s*select/i.test(q) ? st.all(...args) : (st.run(...args), []);
  return { toArray: () => rows };
} };
const obj = new w.Presence({ storage: { sql } }, {});
const env = { TOKEN_SECRET: "x".repeat(40), PRESENCE: { idFromName: () => 1, get: () => obj } };
const UUID = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";
const keys = [mojang.publicKey];

async function proof(serverId, { uuid = UUID, signer = player, certKey = player, expiresAt = Date.now() + 3600_000, certifier = mojang, forUuid = UUID } = {}) {
  const der = new Uint8Array(await subtle.exportKey("spki", certKey.publicKey));
  const payload = new Uint8Array(24 + der.length);
  const h = forUuid.replace(/-/g, "");
  for (let i = 0; i < 16; i++) payload[i] = parseInt(h.slice(i * 2, i * 2 + 2), 16);
  new DataView(payload.buffer).setBigUint64(16, BigInt(expiresAt));
  payload.set(der, 24);
  const keySignature = await subtle.sign("RSASSA-PKCS1-v1_5", certifier.privateKey, payload);
  const signature = await subtle.sign("RSASSA-PKCS1-v1_5", signer.privateKey, new TextEncoder().encode(`tempest-auth|${serverId}|${h}`));
  return { uuid, publicKey: b64(der), expiresAt, keySignature: b64(keySignature), signature: b64(signature) };
}

let ip = 0;
const call = (path, body, token, method = "POST") => w.handle(new Request("https://t.dev" + path, {
  method, body: method === "POST" ? JSON.stringify(body ?? {}) : undefined,
  headers: { "CF-Connecting-IP": "1.2.3." + (ip++ % 200), ...(token ? { Authorization: "Bearer " + token } : {}) },
}), env, keys).then(async (r) => [r.status, await r.json()]);
const start = async (name = "Steve") => (await call("/v1/auth/start", { name }))[1].serverId;
async function signIn(name, uuid, keys2 = player) {
  const sid = await start(name);
  const [st, bd] = await call("/v1/auth/finish", { name, serverId: sid, ...(await proof(sid, { uuid, certKey: keys2, signer: keys2, forUuid: uuid })) });
  assert.equal(st, 200, "sign-in " + name);
  return bd.token;
}
const quiet = console.log; console.log = () => {};

let [s, b] = await call("/health", null, null, "GET"); assert.equal(s, 200); assert.equal(b.online, 0);
[s] = await call("/v1/auth/start", { name: "bad name!" }); assert.equal(s, 400);
let sid = await start(); assert.match(sid, /^[0-9a-f]{40}$/);
[s] = await call("/v1/auth/finish", { name: "Alex", serverId: sid, ...(await proof(sid)) }); assert.equal(s, 400, "code is tied to the name");
[s] = await call("/v1/auth/finish", { name: "Steve", serverId: sid, ...(await proof(sid, { signer: stranger })) }); assert.equal(s, 403, "signed by someone else's key");
[s] = await call("/v1/auth/finish", { name: "Steve", serverId: sid, ...(await proof(sid, { certifier: stranger })) }); assert.equal(s, 403, "key not certified by Mojang");
[s] = await call("/v1/auth/finish", { name: "Steve", serverId: sid, ...(await proof(sid, { expiresAt: Date.now() - 1000 })) }); assert.equal(s, 403, "expired key");
const other = await start();
[s] = await call("/v1/auth/finish", { name: "Steve", serverId: sid, ...(await proof(other)) }); assert.equal(s, 403, "signature for a different code");
const good = await proof(sid);
[s] = await call("/v1/auth/finish", { name: "Steve", serverId: sid, ...good, uuid: "11111111-2222-3333-4444-555555555555" }); assert.equal(s, 403, "certificate is for another uuid");
[s, b] = await call("/v1/auth/finish", { name: "Steve", serverId: sid, ...good }); assert.equal(s, 200); assert.equal(b.uuid, UUID);
const tok = b.token;
[s] = await call("/v1/heartbeat", {}, null); assert.equal(s, 401);
[s] = await call("/v1/heartbeat", {}, tok.slice(0, -1) + (tok.endsWith("A") ? "B" : "A")); assert.equal(s, 401, "tampered token");
[s] = await call("/v1/heartbeat", {}, tok); assert.equal(s, 200);
[s, b] = await call("/v1/lookup", { uuids: [UUID] }, tok); assert.deepEqual(b.tempest, [UUID], "online after first heartbeat");
[s, b] = await call("/v1/lookup", { uuids: [UUID.replace(/-/g, "").toUpperCase(), "nope", "11111111-2222-3333-4444-555555555555"] }, tok);
assert.equal(s, 200); assert.deepEqual(b.tempest, [UUID]);
[s, b] = await call("/health", null, null, "GET"); assert.equal(b.online, 1);
[s] = await call("/v1/lookup", { uuids: new Array(201).fill(UUID) }, tok); assert.equal(s, 400);
[s] = await call("/v1/logout", {}, tok); assert.equal(s, 200);
[s, b] = await call("/v1/lookup", { uuids: [UUID] }, tok); assert.deepEqual(b.tempest, []);
let last; for (let i = 0; i < 125; i++) last = await w.handle(new Request("https://t.dev/v1/heartbeat", { method: "POST", body: "{}", headers: { "CF-Connecting-IP": "9.9.9.9" } }), env, keys);
assert.equal(last.status, 429);
// ---- friends
const A = UUID, B = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee", C = "12345678-1234-4234-8234-123456789abc";
const tA = await signIn("Steve", A), tB = await signIn("Alex", B), tC = await signIn("Carl", C);
[s, b] = await call("/v1/friends/request", { uuid: A }, tA); assert.equal(s, 400, "can't add yourself");
[s, b] = await call("/v1/friends/request", { uuid: "nope" }, tA); assert.equal(s, 400);
[s, b] = await call("/v1/friends/request", { uuid: B }, tA); assert.equal(b.result, "sent");
[s, b] = await call("/v1/friends/request", { uuid: B }, tA); assert.equal(b.result, "sent", "repeat is harmless");
await call("/v1/heartbeat", {}, tA);
[s, b] = await call("/v1/heartbeat", { server: "crystalbox.org" }, tB); assert.equal(b.incoming, 1, "B sees one request");
[s, b] = await call("/v1/friends/list", {}, tB); assert.deepEqual(b.incoming, [A]); assert.deepEqual(b.friends, []);
[s, b] = await call("/v1/friends/list", {}, tA); assert.deepEqual(b.outgoing, [B]);
[s, b] = await call("/v1/friends/accept", { uuid: C }, tB); assert.equal(s, 400, "no request from C");
[s, b] = await call("/v1/friends/accept", { uuid: A }, tB); assert.equal(s, 200);
[s, b] = await call("/v1/friends/list", {}, tA);
assert.deepEqual(b.friends, [{ uuid: B, online: true, server: "crystalbox.org" }]); assert.deepEqual(b.outgoing, []);
// the object restarts (eviction / deploy): presence comes back from storage, friends still see B online
const before = env.PRESENCE.get;
const restarted = new w.Presence({ storage: { sql } }, {});
env.PRESENCE.get = () => restarted;
[s, b] = await call("/v1/friends/list", {}, tA);
assert.deepEqual(b.friends, [{ uuid: B, online: true, server: "crystalbox.org" }], "online survives a restart");
[s, b] = await call("/health", null, null, "GET"); assert.ok(b.online >= 2, "restored online count");
env.PRESENCE.get = before;
// B hides from friends -> offline to A, but still has the public nametag logo
await call("/v1/heartbeat", { server: "x", friends: false, public: true }, tB);
[s, b] = await call("/v1/friends/list", {}, tA); assert.deepEqual(b.friends, [{ uuid: B, online: false, server: null }]);
[s, b] = await call("/v1/lookup", { uuids: [B] }, tA); assert.deepEqual(b.tempest, [B]);
// B hides the logo from strangers but stays visible to friends
await call("/v1/heartbeat", { server: "weird\u0000server", friends: true, public: false }, tB);
[s, b] = await call("/v1/lookup", { uuids: [B] }, tC); assert.deepEqual(b.tempest, [], "hidden from strangers");
[s, b] = await call("/v1/friends/list", {}, tA); assert.equal(b.friends[0].online, true); assert.equal(b.friends[0].server, "weirdserver");
// mutual requests become a friendship
[s, b] = await call("/v1/friends/request", { uuid: C }, tA); assert.equal(b.result, "sent");
[s, b] = await call("/v1/friends/request", { uuid: A }, tC); assert.equal(b.result, "accepted");
[s, b] = await call("/v1/friends/list", {}, tC); assert.deepEqual(b.friends.map((f) => f.uuid), [A]);
// decline / cancel / remove
[s, b] = await call("/v1/friends/request", { uuid: C }, tB); assert.equal(b.result, "sent");
[s, b] = await call("/v1/friends/decline", { uuid: B }, tC); assert.equal(s, 200);
[s, b] = await call("/v1/friends/list", {}, tB); assert.deepEqual(b.outgoing, []);
[s, b] = await call("/v1/friends/remove", { uuid: A }, tB); assert.equal(s, 200);
[s, b] = await call("/v1/friends/list", {}, tA); assert.deepEqual(b.friends.map((f) => f.uuid), [C], "removal is mutual");
[s, b] = await call("/v1/friends/list", {}, null); assert.equal(s, 401, "needs sign-in");
console.log = quiet;
console.log("all worker tests passed");
