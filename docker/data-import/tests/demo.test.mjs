import assert from "node:assert/strict";
import test from "node:test";
import { demoAccounts, requireDemoSetup, prepareDemo } from "../demo.mjs";

test("demo requires explicit permission and a nontrivial password", () => {
  assert.throws(() => requireDemoSetup({}), /explicitly/);
  assert.throws(() => requireDemoSetup({ DEMO_SETUP_ENABLED: "true", DEMO_PASSWORD: "short" }), /12 to 128/);
  assert.doesNotThrow(() => requireDemoSetup({ DEMO_SETUP_ENABLED: "true", DEMO_PASSWORD: "demo-test-password" }));
});

test("demo accounts are separate, have valid RUNs and only one academic role each", () => {
  assert.deepEqual(demoAccounts.map(p => p.username), ["admin", "teacher", "student"]);
  for (const person of demoAccounts) {
    const [number, check] = person.run.split("-");
    const sum = [...number].reverse().reduce((total, digit, index) => total + Number(digit) * (2 + index % 6), 0);
    const result = 11 - sum % 11;
    assert.equal(check, result === 11 ? "0" : result === 10 ? "K" : String(result));
  }
});

test("reserved period conflicts abort before any account changes", async () => {
  const queries = [];
  const db = { query: async (sql) => {
    queries.push(sql);
    if (sql.includes("FROM degree_programs")) return { rows: [] };
    if (sql.includes("FROM academic_periods")) return { rowCount: 1 };
    return { rows: [] };
  } };
  await assert.rejects(prepareDemo(db, {}, "demo-test-password"), /2099-1 already exists/);
  assert.equal(queries.at(-1), "ROLLBACK");
  assert.ok(!queries.some(sql => /^(INSERT|UPDATE|DELETE)\b/.test(sql)));
});

test("unrelated program conflicts never adopt existing users", async () => {
  const db = { query: async sql => ({ rows: sql.includes("FROM degree_programs") ? [{ description: "Unrelated program" }] : [] }) };
  await assert.rejects(prepareDemo(db, {}, "demo-test-password"), /unrelated data/);
});
