import pg from "pg";
import { KeycloakAdmin } from "./keycloak.mjs";
import { prepareDemo, requireDemoSetup } from "./demo.mjs";

let db;
try {
  requireDemoSetup(process.env);
  db = new pg.Client();
  await db.connect();
  const result = await prepareDemo(db, new KeycloakAdmin(process.env), process.env.DEMO_PASSWORD);
  console.log(result.existing
    ? "Demo already exists. Accounts, passwords and academic progress were not changed."
    : "Demo ready: admin / teacher / student; DEMO-SIGA plan, 7 courses, period 2099-1, enrollment and 7 sections. No registrations, grades or history were fabricated.");
} catch (error) {
  console.error(error instanceof Error && !error.code && !error.cause
    ? error.message : "Demo setup failed; check database/IAM availability. No credentials were logged.");
  process.exitCode = 1;
} finally {
  if (db) await db.end();
}
