import assert from "node:assert/strict";
import pg from "pg";
import { KeycloakAdmin } from "../keycloak.mjs";
import { demoAccounts, prepareDemo } from "../demo.mjs";
import { initializeOfficialDemo } from "../official-demo.mjs";

if (process.env.ISOLATED_OFFICIAL_DEMO_TEST !== "true" || process.env.PGDATABASE !== "siga_official_demo_test")
  throw new Error("Disposable official demo database required");
const db = new pg.Client();
const kc = new KeycloakAdmin(process.env);
await db.connect();
try {
  // Reset ONLY the disposable test fixture, never the root .env/main project.
  for (const person of demoAccounts) {
    const account = await kc.findUser(person.username);
    if (account) await kc.request("DELETE", `/users/${account.id}`);
  }
  await db.query("TRUNCATE app_users, degree_programs, academic_periods, app_seed_state RESTART IDENTITY CASCADE");
  await prepareDemo(db, kc, process.env.DEMO_PASSWORD);
  const before = (await db.query("SELECT id,keycloak_subject FROM app_users ORDER BY id")).rows;
  await initializeOfficialDemo(db, kc, "a-different-password-not-applied");
  assert.deepEqual((await db.query("SELECT id,keycloak_subject FROM app_users ORDER BY id")).rows, before);
  assert.equal((await db.query("SELECT count(*)::int AS n FROM courses")).rows[0].n, 79);
  assert.equal((await db.query("SELECT count(*)::int AS n FROM sections")).rows[0].n, 14);
  assert.equal((await db.query("SELECT count(*)::int AS n FROM enrollments")).rows[0].n, 2);
  assert.equal((await db.query("SELECT d.code FROM students s JOIN study_plans p ON p.id=s.study_plan_id JOIN degree_programs d ON d.id=p.degree_program_id")).rows[0].code, "10450");
  console.log("PASS: legacy demo accounts/subjects and old scenario preserved, unused student migrated to official TAP plan, no password reset.");
} finally { await db.end(); }
