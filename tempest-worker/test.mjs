// Local test of the Worker logic (no Cloudflare needed): node test.mjs
import { readFileSync, writeFileSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import assert from "node:assert/strict";

const src = readFileSync(new URL("./src/index.js", import.meta.url), "utf8")
  .replace('import { DurableObject } from "cloudflare:workers";', "class DurableObject { constructor(ctx, env) { this.ctx = ctx; this.env = env; } }");
const dir = mkdtempSync(join(tmpdir(), "tw-"));
writeFileSync(join(dir, "w.mjs"), src);
const w = await import(join(dir, "w.mjs"));

const obj = new w.Presence({}, {});
const env = { TOKEN_SECRET: "x".repeat(40), PRESENCE: { idFromName: () => 1, get: () => obj } };
const UUID = "0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";
const fakeMojang = async (name, sid) => (name === "Steve" ? UUID : null);
let ip = 0;
const call = (path, body, token, method = "POST") => w.handle(new Request("https://t.dev" + path, {
  method, body: method === "POST" ? JSON.stringify(body ?? {}) : undefined,
  headers: { "CF-Connecting-IP": "1.2.3." + (ip++ % 200), ...(token ? { Authorization: "Bearer " + token } : {}) },
}), env, fakeMojang).then(async (r) => [r.status, await r.json()]);

let [s, b] = await call("/health", null, null, "GET"); assert.equal(s, 200); assert.equal(b.online, 0);
[s, b] = await call("/v1/auth/start", { name: "bad name!" }); assert.equal(s, 400);
[s, b] = await call("/v1/auth/start", { name: "Steve" }); assert.equal(s, 200); assert.match(b.serverId, /^[0-9a-f]{40}$/);
const sid = b.serverId;
[s] = await call("/v1/auth/finish", { name: "Alex", serverId: sid }); assert.equal(s, 400, "code is tied to the name");
[s] = await call("/v1/auth/finish", { name: "Steve", serverId: sid.slice(0, 39) + (sid[39] === "a" ? "b" : "a") }); assert.equal(s, 400, "tampered code");
[s, b] = await call("/v1/auth/finish", { name: "Steve", serverId: sid }); assert.equal(s, 200); assert.equal(b.uuid, UUID);
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
// Mojang says no
[s, b] = await call("/v1/auth/start", { name: "Herobrine" });
[s] = await call("/v1/auth/finish", { name: "Herobrine", serverId: b.serverId }); assert.equal(s, 403);
// rate limit
let last; for (let i = 0; i < 125; i++) last = await w.handle(new Request("https://t.dev/v1/heartbeat", { method: "POST", body: "{}", headers: { "CF-Connecting-IP": "9.9.9.9" } }), env);
assert.equal(last.status, 429);
console.log("all worker tests passed");
