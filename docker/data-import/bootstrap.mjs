import pg from "pg";
import { KeycloakAdmin } from "./keycloak.mjs";
import { demoStartupEnabled, initializeOfficialDemo } from "./official-demo.mjs";
import { initializeFullOffering } from "./full-offering.mjs";

let db;
try {
  if (!demoStartupEnabled(process.env)) console.log("Automatic demo initialization disabled.");
  else {
    db = new pg.Client();
    await db.connect();
    const kc = new KeycloakAdmin(process.env);
    const result = await initializeOfficialDemo(db, kc, process.env.DEMO_PASSWORD);
    console.log(result.existing
      ? "Demo already initialized: catalog, accounts, passwords and academic progress preserved."
      : "Official demo ready: 3 programs, 72 courses, 75 prerequisite links; admin/teacher/student; period 2099-2, enrollment and 7 valid sections. No grades or history fabricated.");
    const offering = await initializeFullOffering(db, kc, process.env.DEMO_PASSWORD);
    console.log(offering.existing ? "Full official offering already initialized; existing activity preserved."
      : `Full official offering ready: 72 sections in ${offering.period}; ${offering.addedTeachers} additional teachers; ${offering.enrolled} additional enrollments. No registrations or grades fabricated.`);
  }
} catch (error) {
  console.error(error instanceof Error && !error.code && !error.cause
    ? error.message : "Automatic demo initialization failed; no credentials were logged.");
  process.exitCode = 1;
} finally {
  if (db) await db.end();
}
