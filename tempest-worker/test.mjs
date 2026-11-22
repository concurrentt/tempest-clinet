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

const obj = new w.Presence({}, {});
const env = { TOKEN_SECRET: "x".repeat(40), PRESENCE: { idFromName: () => 1, get: () => obj } };
const UUID = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";
const keys = [mojang.publicKey];

async function proof(serverId, { uuid = UUID, signer = player, certKey = player, expiresAt = Date.now() + 3600_000, certifier = mojang } = {}) {
  const der = new Uint8Array(await subtle.exportKey("spki", certKey.publicKey));
  const payload = new Uint8Array(24 + der.length);
  const h = UUID.replace(/-/g, "");
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
[s, b] = await call("/v1/lookup", { uuids: [UUID.replace(/-/g, "").toUpperCase(), "nope", "11111111-2222-3333-4444-555555555555"] }, tok);
assert.equal(s, 200); assert.deepEqual(b.tempest, [UUID]);
[s, b] = await call("/health", null, null, "GET"); assert.equal(b.online, 1);
[s] = await call("/v1/lookup", { uuids: new Array(201).fill(UUID) }, tok); assert.equal(s, 400);
[s] = await call("/v1/logout", {}, tok); assert.equal(s, 200);
[s, b] = await call("/v1/lookup", { uuids: [UUID] }, tok); assert.deepEqual(b.tempest, []);
let last; for (let i = 0; i < 125; i++) last = await w.handle(new Request("https://t.dev/v1/heartbeat", { method: "POST", body: "{}", headers: { "CF-Connecting-IP": "9.9.9.9" } }), env, keys);
assert.equal(last.status, 429);
console.log = quiet;
console.log("all worker tests passed");
