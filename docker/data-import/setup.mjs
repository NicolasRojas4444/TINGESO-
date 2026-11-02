import pg from "pg";
import { loadCatalog, importCatalog } from "./catalog.mjs";
import { requireLocalSetup, prepareAccounts } from "./accounts.mjs";
import { KeycloakAdmin } from "./keycloak.mjs";

const command = process.argv[2] || "all";
let client;
try {
  requireLocalSetup(process.env, command);
  for (const name of ["PGHOST", "PGDATABASE", "PGUSER", "PGPASSWORD"]) {
    if (!process.env[name]) throw new Error(`Missing ${name}`);
  }
  const catalog = await loadCatalog();
  client = new pg.Client();
  await client.connect();
  let plans;
  if (command !== "accounts") {
    plans = await importCatalog(client, catalog);
    const prerequisites = catalog.programs.reduce(
      (total, program) =>
        total +
        program.courses.reduce(
          (count, course) => count + course.prerequisites.length,
          0,
        ),
      0,
    );
    console.log(
      `Catalog ready: 3 official current plans, 72 courses, ${prerequisites} prerequisite links; no sections, grades or enrollment were created`,
    );
  }
  if (command !== "catalog") {
    if (!plans) {
      const {
        rows: [plan],
      } =
        await client.query(`SELECT p.id FROM study_plans p JOIN degree_programs d ON d.id=p.degree_program_id
        WHERE d.code='10450' AND p.plan_version='2021.3'`);
      if (!plan) throw new Error("Import the official catalog first");
      plans = new Map([["10450", plan.id]]);
    }
    await prepareAccounts(
      client,
      new KeycloakAdmin(process.env),
      plans.get("10450"),
      process.env.LOCAL_TEST_PASSWORD,
    );
    console.log(
      "Only the three selected siga accounts were prepared. Other users and master were not modified. Sign out and sign in again.",
    );
  }
} catch (error) {
  // Database/provider errors can include secret request data; never dump raw errors.
  const safe =
    error instanceof Error && !error.code && !error.cause
      ? error.message
      : "Provider or database operation failed; existing data was not overwritten automatically";
  console.error(`Local setup stopped: ${safe}`);
  console.error(
    "Catalog and profile/password setup are separate operations; review progress above before retrying.",
  );
  process.exitCode = 1;
} finally {
  if (client) await client.end();
}
