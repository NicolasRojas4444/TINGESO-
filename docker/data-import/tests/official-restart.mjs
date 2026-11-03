import assert from "node:assert/strict";
import pg from "pg";

if (process.env.ISOLATED_OFFICIAL_DEMO_TEST !== "true" || process.env.PGDATABASE !== "siga_official_demo_test")
  throw new Error("Disposable official demo database required");
const db = new pg.Client();
await db.connect();
try {
  assert.equal((await db.query("SELECT count(*)::int AS n FROM course_registrations")).rows[0].n, 6);
  assert.equal((await db.query("SELECT count(*)::int AS n FROM academic_records")).rows[0].n, 6);
  assert.equal((await db.query("SELECT name FROM courses WHERE code='TAP101'")).rows[0].name, "User edited course");
  assert.equal((await db.query("SELECT status FROM academic_periods WHERE academic_year=2099 AND semester=2")).rows[0].status, "CLOSED");
  assert.equal((await db.query("SELECT count(*)::int AS n FROM courses")).rows[0].n, 72);
  console.log("PASS: real Compose restart kept registrations, history, closed period and user-edited catalog without duplication.");
} finally { await db.end(); }
