import assert from "node:assert/strict";
import test from "node:test";
import { demoStartupEnabled, demoPeriod, initializeOfficialDemo } from "../official-demo.mjs";

test("automatic demo is off by default and accepts the explicitly selected demo password", () => {
  assert.equal(demoStartupEnabled({}), false);
  assert.equal(demoStartupEnabled({ DEMO_SETUP_ENABLED: "false" }), false);
  assert.throws(() => demoStartupEnabled({ DEMO_SETUP_ENABLED: "yes" }), /true or false/);
  assert.throws(() => demoStartupEnabled({ DEMO_SETUP_ENABLED: "true" }), /DEMO_PASSWORD/);
  assert.equal(demoStartupEnabled({ DEMO_SETUP_ENABLED: "true", DEMO_PASSWORD: "tuki123" }), true);
  assert.deepEqual(demoPeriod, { year: 2099, semester: 2 });
});

test("completed startup never contacts IAM, imports the catalog or overwrites progress", async () => {
  const statements = [];
  const db = { query: async sql => {
    statements.push(sql);
    return { rowCount: sql.startsWith("SELECT 1 FROM app_seed_state") ? 1 : 0 };
  } };
  assert.deepEqual(await initializeOfficialDemo(db, {}, "not-applied"), { existing: true });
  assert.ok(!statements.some(sql => /^(INSERT|UPDATE|DELETE)\b/.test(sql)));
  assert.ok(statements.at(-1).includes("pg_advisory_unlock"));
});
