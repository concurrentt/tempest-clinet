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
// stand-ins for Cloudflare's hibernating WebSockets
const sockets = [];
class FakeWs { constructor(tag) { this.tag = tag; this.att = null; this.sent = []; }
  serializeAttachment(a) { this.att = JSON.parse(JSON.stringify(a)); } deserializeAttachment() { return this.att; }
  send(t) { this.sent.push(JSON.parse(t)); } close() {} }
const ctxFake = { storage: { sql }, getWebSockets: () => sockets, acceptWebSocket: (ws) => sockets.push(ws) };
const obj = new w.Presence(ctxFake, {});
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
const restarted = new w.Presence(ctxFake, {});
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

// ---- cosmetics: saved, cleaned, returned by lookup for online Tempest users
[s, b] = await call("/v1/cosmetics", { cape: "Galaxy", wings: "Angel", head: "Halo", halo: "Gold", wingSize: 9, evil: "<script>" }, tA);
assert.equal(s, 200); assert.deepEqual(b.c, { cape: "Galaxy", wings: "Angel", head: "Halo", halo: "Gold", wingSize: 1.6 });
[s] = await call("/v1/cosmetics", { wings: "<img src=x>" }, tA); assert.equal(s, 200);
[s, b] = await call("/v1/cosmetics", { cape: "Galaxy", wings: "Angel" }, tA);
await call("/v1/heartbeat", {}, tA);
[s, b] = await call("/v1/lookup", { uuids: [A, C] }, tC);
assert.deepEqual(b.tempest, [A]); assert.deepEqual(b.cosmetics[A], { cape: "Galaxy", wings: "Angel" });

// ---- live channel: emotes and cosmetic changes reach only connections watching that player
const wsA = new FakeWs(A), wsB = new FakeWs(B), wsC = new FakeWs(C);
for (const [ws, u] of [[wsA, A], [wsB, B], [wsC, C]]) { sockets.push(ws); ws.serializeAttachment({ u, w: "" }); }
obj.webSocketMessage(wsB, JSON.stringify({ t: "watch", u: A.replace(/-/g, "") + C.replace(/-/g, "") }));   // B has A and C nearby
obj.webSocketMessage(wsA, JSON.stringify({ t: "emote", id: "wave" }));
assert.deepEqual(wsB.sent, [{ t: "emote", uuid: A, id: "wave" }], "B (watching A) gets it");
assert.equal(wsC.sent.length, 0, "C (not watching A) gets nothing"); assert.equal(wsA.sent.length, 0, "not echoed to sender");
obj.webSocketMessage(wsA, JSON.stringify({ t: "emote", id: "<bad id>" }));
assert.deepEqual(wsB.sent.at(-1), { t: "emote", uuid: A, id: null }, "bad ids become stop");
obj.webSocketMessage(wsA, "not json"); obj.webSocketMessage(wsA, JSON.stringify({ t: "watch", u: 5 }));
await call("/v1/cosmetics", { wings: "Demon" }, tA);
assert.deepEqual(wsB.sent.at(-1), { t: "cos", uuid: A, c: { wings: "Demon" } }, "cosmetic change pushed live");
for (let i = 0; i < 40; i++) obj.webSocketMessage(wsC, JSON.stringify({ t: "emote", id: "dance" }));
assert.ok(wsB.sent.filter((m) => m.uuid === C).length <= 30, "socket rate limit");

// ---- chat: private messages only between friends (A and C are friends; A and B are not any more)
await new Promise((r) => setTimeout(r, 10_100));       // let the socket rate limit window pass
wsA.sent.length = 0; wsB.sent.length = 0; wsC.sent.length = 0;
obj.webSocketMessage(wsA, JSON.stringify({ t: "msg", to: C, text: " hi\u00a7c there ", n: "Steve" }));
assert.equal(wsC.sent.at(-1).t, "msg"); assert.equal(wsC.sent.at(-1).text, "hic there"); assert.equal(wsC.sent.at(-1).from, A);
assert.equal(wsC.sent.at(-1).n, "Steve");
obj.webSocketMessage(wsA, JSON.stringify({ t: "msg", to: B, text: "psst" }));
assert.equal(wsB.sent.length, 0, "no messages to non-friends"); assert.deepEqual(wsA.sent.at(-1), { t: "msgfail", to: B, reason: "not friends" });
obj.webSocketMessage(wsA, JSON.stringify({ t: "msg", to: C, text: "x".repeat(999), n: "bad name!" }));
assert.equal(wsC.sent.at(-1).text.length, 256); assert.equal(wsC.sent.at(-1).n, null);
// ---- stored DMs: kept, readable by both friends, newest per conversation in "recent"
{
  const h = obj.dmHistory(A, C);
  assert.ok(Array.isArray(h.messages) && h.messages.length >= 1, "history has the message");
  assert.deepEqual(obj.dmHistory(A, B), { error: "not friends" });
  const r = obj.dmRecent(C);
  assert.equal(r.conversations[0].uuid, A);
}
// ---- invites: friends only, plain server addresses only
obj.webSocketMessage(wsA, JSON.stringify({ t: "invite", to: C, server: " Play.Example.NET:25565 ", n: "Steve" }));
assert.deepEqual({ ...wsC.sent.at(-1), at: 0 }, { t: "invite", from: A, n: "Steve", server: "play.example.net:25565", at: 0 });
obj.webSocketMessage(wsA, JSON.stringify({ t: "invite", to: B, server: "play.example.net" }));
assert.deepEqual(wsA.sent.at(-1), { t: "invitefail", to: B, reason: "not friends" });
obj.webSocketMessage(wsA, JSON.stringify({ t: "invite", to: C, server: "evil.com/\u00a7c path" }));
assert.deepEqual(wsA.sent.at(-1), { t: "invitefail", to: C, reason: "bad server" });

// ---- groups and party
[s, b] = await call("/v1/chat/group/create", { name: "PvP Squad", members: [C] }, tA); assert.equal(s, 200);
const g = b.g; assert.deepEqual(g.members, [A, C]); assert.equal(g.kind, "group");
assert.equal(wsC.sent.at(-1).t, "group", "members hear about the new group");
[s, b] = await call("/v1/chat/group/create", { name: "x", members: [B] }, tA); assert.equal(s, 400, "only friends");
[s, b] = await call("/v1/chat/group/create", { name: "<script>", members: [C] }, tA); assert.equal(s, 400, "bad name");
[s, b] = await call("/v1/chat/groups", {}, tC); assert.deepEqual(b.groups.map((x) => x.id), [g.id]);
[s, b] = await call("/v1/chat/groups", {}, tB); assert.deepEqual(b.groups, []);
obj.webSocketMessage(wsC, JSON.stringify({ t: "msg", g: g.id, text: "gg", n: "Alex" }));
assert.deepEqual({ ...wsA.sent.at(-1), at: 0 }, { t: "msg", from: C, n: "Alex", g: g.id, gn: "PvP Squad", text: "gg", at: 0 });
obj.webSocketMessage(wsB, JSON.stringify({ t: "msg", g: g.id, text: "let me in" }));
assert.deepEqual(wsB.sent.at(-1), { t: "msgfail", g: g.id, reason: "no such group" }, "outsiders can't post");
[s, b] = await call("/v1/chat/group/update", { id: g.id, name: "Renamed" }, tC); assert.equal(s, 400, "only the owner edits");
[s, b] = await call("/v1/chat/group/update", { id: g.id, name: "Squad 2" }, tA); assert.equal(b.g.name, "Squad 2");
[s, b] = await call("/v1/chat/group/create", { kind: "party", members: [C] }, tA); assert.equal(b.g.kind, "party"); assert.equal(b.g.name, "Party");
const party = b.g;
[s, b] = await call("/v1/chat/group/create", { kind: "party", members: [C] }, tA); assert.equal(b.g.id, party.id, "one party each");
[s, b] = await call("/v1/chat/group/leave", { id: party.id }, tC); assert.equal(s, 200);
assert.deepEqual(wsC.sent.at(-1), { t: "group_gone", id: party.id });
[s, b] = await call("/v1/chat/groups", {}, tA); assert.deepEqual(b.groups.find((x) => x.id === party.id).members, [A]);
[s, b] = await call("/v1/chat/group/leave", { id: g.id }, tA); assert.equal(s, 200, "owner leaving ends it");
[s, b] = await call("/v1/chat/groups", {}, tC); assert.deepEqual(b.groups, []);

// ---- sign-in without a chat key: Mojang's hasJoined check (the access token only ever goes to Mojang)
{
  const joins = new Map();   // "name|serverId" -> profile Mojang would return
  const fakeMojang = async (name, sid) => {
    const p = joins.get(name.toLowerCase() + "|" + sid);
    return p ? new Response(JSON.stringify(p), { status: 200 }) : new Response(null, { status: 204 });
  };
  const callJ = (body) => w.handle(new Request("https://t.dev/v1/auth/finish", {
    method: "POST", body: JSON.stringify(body), headers: { "CF-Connecting-IP": "9.9.9." + (ip++ % 200) },
  }), env, keys, fakeMojang).then(async (r) => [r.status, await r.json()]);
  const JU = "abcdefab-1234-5678-9abc-def012345678";
  let sid = await start("Rres");
  let [st, bd] = await callJ({ name: "Rres", serverId: sid, method: "joined" });
  assert.equal(st, 403, "no join recorded at Mojang");
  joins.set("rres|" + sid, { id: JU.replace(/-/g, ""), name: "Rres" });
  [st, bd] = await callJ({ name: "Rres", serverId: sid, method: "joined" });
  assert.equal(st, 200, "joined sign-in");
  assert.equal(bd.uuid, JU);
  const [hs] = await call("/v1/heartbeat", {}, bd.token);
  assert.equal(hs, 200, "joined token works");
  // Mojang answering with another account is refused
  sid = await start("Rres");
  joins.set("rres|" + sid, { id: JU.replace(/-/g, ""), name: "SomeoneElse" });
  [st] = await callJ({ name: "Rres", serverId: sid, method: "joined" });
  assert.equal(st, 403, "name mismatch");
  // the code is still tied to the name and must be ours
  [st] = await callJ({ name: "Other", serverId: sid, method: "joined" });
  assert.equal(st, 400, "code for another name");
  [st] = await callJ({ name: "Rres", serverId: "f".repeat(40), method: "joined" });
  assert.equal(st, 400, "made-up code");
}

console.log = quiet;
console.log("all worker tests passed");
