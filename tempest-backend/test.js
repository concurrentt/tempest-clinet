// End-to-end test against a local copy of the service (Mojang check skipped). Run: npm test
process.env.PORT = "18765";
process.env.TEMPEST_DEV_SKIP_MOJANG = "1";
const { server } = require("./server");
const assert = require("assert");

const base = "http://127.0.0.1:18765";

async function call(path, body, token) {
  const res = await fetch(base + path, {
    method: body === undefined ? "GET" : "POST",
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: "Bearer " + token } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  return { status: res.status, body: await res.json() };
}

async function signIn(name) {
  const s = await call("/v1/auth/start", { name });
  assert.strictEqual(s.status, 200, "start");
  const f = await call("/v1/auth/finish", { name, serverId: s.body.serverId });
  assert.strictEqual(f.status, 200, "finish");
  return f.body;
}

(async () => {
  let r = await call("/health");
  assert.strictEqual(r.status, 200);

  const alice = await signIn("Alice");
  const bob = await signIn("Bob_2");
  const stranger = "0f0f0f0f-0f0f-0f0f-0f0f-0f0f0f0f0f0f";

  // both show up; the stranger doesn't
  r = await call("/v1/lookup", { uuids: [bob.uuid, stranger, "not-a-uuid"] }, alice.token);
  assert.strictEqual(r.status, 200);
  assert.deepStrictEqual(r.body.tempest, [bob.uuid]);

  // undashed upper-case UUIDs are accepted too
  r = await call("/v1/lookup", { uuids: [bob.uuid.replace(/-/g, "").toUpperCase()] }, alice.token);
  assert.deepStrictEqual(r.body.tempest, [bob.uuid]);

  // heartbeat works
  r = await call("/v1/heartbeat", {}, bob.token);
  assert.strictEqual(r.status, 200);

  // after logout Bob is no longer online
  r = await call("/v1/logout", {}, bob.token);
  assert.strictEqual(r.status, 200);
  r = await call("/v1/lookup", { uuids: [bob.uuid] }, alice.token);
  assert.deepStrictEqual(r.body.tempest, []);

  // no / bad token is refused
  r = await call("/v1/lookup", { uuids: [] });
  assert.strictEqual(r.status, 401);
  r = await call("/v1/heartbeat", {}, "x".repeat(32));
  assert.strictEqual(r.status, 401);

  // a code can't be reused, names are checked, lookups are capped
  const s = await call("/v1/auth/start", { name: "Carol" });
  r = await call("/v1/auth/finish", { name: "Mallory", serverId: s.body.serverId });
  assert.strictEqual(r.status, 400);
  r = await call("/v1/auth/start", { name: "bad name!" });
  assert.strictEqual(r.status, 400);
  r = await call("/v1/lookup", { uuids: new Array(201).fill(stranger) }, alice.token);
  assert.strictEqual(r.status, 400);

  console.log("all tests passed");
  server.close();
  process.exit(0);
})().catch((e) => {
  console.error("TEST FAILED:", e);
  process.exit(1);
});
