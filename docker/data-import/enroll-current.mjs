import pg from "pg";
import { demoStartupEnabled } from "./official-demo.mjs";
import { enrollCurrentStudents } from "./full-offering.mjs";
import { inTransaction } from "./catalog.mjs";

if (!demoStartupEnabled(process.env)) throw new Error("Explicit test-data setup must be enabled");
const [year, semester] = process.argv.slice(2).map(Number);
if (!Number.isInteger(year) || ![1, 2].includes(semester) || process.argv.length !== 4)
  throw new Error("Usage: node enroll-current.mjs YEAR SEMESTER (explicit OPEN period required)");
const db = new pg.Client();
try {
  await db.connect();
  await inTransaction(db, async () => {
    const period = (await db.query("SELECT id FROM academic_periods WHERE academic_year=$1 AND semester=$2 AND status='OPEN' FOR UPDATE", [year, semester])).rows[0];
    if (!period) throw new Error("The requested period does not exist or is closed; nothing was changed");
    console.log(`External test enrollment: ${await enrollCurrentStudents(db, period.id)} added in ${year}-${semester}. Existing enrollments preserved.`);
  });
} finally { await db.end(); }
