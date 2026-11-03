import { importCatalog, inTransaction, loadCatalog } from "./catalog.mjs";
import { demoAccounts } from "./demo.mjs";

const seedKey = "official-demo-v1";
export const demoPeriod = { year: 2099, semester: 2 };

export function demoStartupEnabled(env) {
  if (![undefined, "false", "true"].includes(env.DEMO_SETUP_ENABLED))
    throw new Error("DEMO_SETUP_ENABLED must be true or false");
  if (env.DEMO_SETUP_ENABLED !== "true") return false;
  // This explicit demo-only password does not change application/IAM policies.
  if (!env.DEMO_PASSWORD || env.DEMO_PASSWORD.length < 7 || env.DEMO_PASSWORD.length > 128 || !env.DEMO_PASSWORD.trim())
    throw new Error("Set DEMO_PASSWORD (7 to 128 characters) for automatic demo initialization");
  return true;
}

export async function initializeOfficialDemo(db, kc, password, log = console.log) {
  const created = [];
  await db.query("SELECT pg_advisory_lock(hashtext('siga-official-demo-bootstrap'))");
  try {
    await db.query("CREATE TABLE IF NOT EXISTS app_seed_state (seed_key varchar(100) PRIMARY KEY, completed_at timestamptz NOT NULL)");
    if ((await db.query("SELECT 1 FROM app_seed_state WHERE seed_key=$1", [seedKey])).rowCount)
      return { existing: true };

    const catalog = await loadCatalog();
    const plans = await importCatalog(db, catalog);
    return await inTransaction(db, async () => {
      const people = [];
      // Preflight all usernames and reserved identities before changing any IAM account.
      for (const person of demoAccounts) {
        const account = await kc.findUser(person.username);
        const local = account ? (await db.query("SELECT * FROM app_users WHERE keycloak_subject=$1 FOR UPDATE", [account.id])).rows[0] : null;
        const collisions = await db.query("SELECT id FROM app_users WHERE (run=$1 OR lower(email)=lower($2)) AND id IS DISTINCT FROM $3::bigint", [person.run, person.email, local?.id || null]);
        if (collisions.rowCount || (account && (!local || local.run !== person.run || local.email !== person.email || account.email !== person.email)))
          throw new Error(`Username or reserved identity ${person.username} belongs to other data; no account was overwritten`);
        if (account && (!account.enabled || local.access_suspended))
          throw new Error(`Account ${person.username} is disabled/suspended; recover it explicitly before initializing the demo`);
        if (!account && (await kc.request("GET", `/users?email=${encodeURIComponent(person.email)}&exact=true`)).length)
          throw new Error(`Reserved email for ${person.username} is in use; no identity was adopted`);
        if (account) {
          const roles = await kc.request("GET", `/users/${account.id}/role-mappings/realm/composite`);
          if (!roles.some(role => role.name === person.role) || roles.some(role => ["ADMIN", "TEACHER", "STUDENT"].includes(role.name) && role.name !== person.role))
            throw new Error(`Unexpected academic roles for ${person.username}; no roles were overwritten`);
        }
        people.push({ person, account, local, role: await kc.request("GET", `/roles/${person.role}`) });
      }
      if ((await db.query("SELECT 1 FROM academic_periods WHERE academic_year=$1 AND semester=$2", [demoPeriod.year, demoPeriod.semester])).rowCount)
        throw new Error("Demo period 2099-2 is already in use; existing periods were not changed");

      const insert = async (sql, values = []) => (await db.query(sql + " RETURNING id", values)).rows[0].id;
      for (const entry of people) {
        const { person, role } = entry;
        if (!entry.account) {
          const response = await kc.request("POST", "/users", {
            username: person.username, email: person.email, firstName: "Demo", lastName: person.role,
            enabled: false, emailVerified: false, requiredActions: [],
            credentials: [{ type: "password", value: password, temporary: false }],
          });
          const location = response.headers.get("location");
          if (!location) throw new Error("Keycloak returned no account ID; manual reconciliation required");
          const subject = location.split("/").pop();
          created.push(subject);
          await kc.request("POST", `/users/${subject}/role-mappings/realm`, [role]);
          entry.local = { id: await insert("INSERT INTO app_users(row_version,keycloak_subject,run,full_name,email,access_suspended) VALUES (0,$1,$2,$3,$4,false)", [subject, person.run, person.name, person.email]) };
        }
      }
      const teacherUser = people.find(entry => entry.person.role === "TEACHER").local.id;
      const studentUser = people.find(entry => entry.person.role === "STUDENT").local.id;
      let teacher = (await db.query("SELECT * FROM teachers WHERE user_id=$1 FOR UPDATE", [teacherUser])).rows[0];
      if (teacher && teacher.status !== "ACTIVE") throw new Error("The demo teacher is inactive; its state was preserved");
      if (!teacher) teacher = { id: await insert("INSERT INTO teachers(row_version,user_id,professional_title,academic_degree,status) VALUES (0,$1,'Ingeniero en Informática','Licenciado','ACTIVE')", [teacherUser]) };
      let student = (await db.query("SELECT * FROM students WHERE user_id=$1 FOR UPDATE", [studentUser])).rows[0];
      const targetPlan = plans.get("10450");
      if (!student) student = { id: await insert("INSERT INTO students(row_version,user_id,study_plan_id,academic_status) VALUES (0,$1,$2,'REGULAR')", [studentUser, targetPlan]) };
      else if (String(student.study_plan_id) !== String(targetPlan)) {
        const active = (await db.query("SELECT EXISTS(SELECT 1 FROM course_registrations WHERE student_id=$1) AS registrations", [student.id])).rows[0].registrations;
        if (!active && student.academic_status === "REGULAR") {
          await db.query("UPDATE students SET study_plan_id=$1,row_version=row_version+1 WHERE id=$2", [targetPlan, student.id]);
          log("Existing demo student moved to the official plan; no registrations or history existed.");
        } else log("Existing student's plan and academic progress preserved; official offering is available for new profiles.");
      }
      const period = await insert("INSERT INTO academic_periods(row_version,academic_year,semester,status) VALUES (0,$1,$2,'OPEN')", [demoPeriod.year, demoPeriod.semester]);
      await db.query("INSERT INTO enrollments(row_version,student_id,academic_period_id,enrolled_at) VALUES (0,$1,$2,now())", [student.id, period]);
      const offerings = (await db.query("SELECT * FROM courses WHERE study_plan_id=$1 AND (semester=1 OR code='TAP201') ORDER BY semester,code", [targetPlan])).rows;
      let slot = 0;
      for (const course of offerings) {
        const section = await insert("INSERT INTO sections(row_version,course_id,academic_period_id,teacher_id,capacity) VALUES (0,$1,$2,$3,30)", [course.id, period, teacher.id]);
        const blocks = (course.theory_hours + course.exercise_hours + course.laboratory_hours) / 2;
        if (!Number.isInteger(blocks) || slot + blocks > 30) throw new Error("Official demo schedule cannot fit the required TEL blocks");
        for (let index = 0; index < blocks; index++, slot++) {
          const day = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"][Math.floor(slot / 6)];
          await db.query("INSERT INTO section_schedule_blocks(row_version,section_id,day_of_week,module) VALUES (0,$1,$2,$3)", [section, day, slot % 6 + 1]);
        }
      }
      for (const subject of created) await kc.request("PUT", `/users/${subject}`, { enabled: true });
      await db.query("INSERT INTO app_seed_state(seed_key,completed_at) VALUES ($1,now())", [seedKey]);
      return { existing: false };
    });
  } catch (error) {
    for (const subject of created) {
      try { await kc.request("DELETE", `/users/${subject}`); }
      catch { console.error(`Incomplete demo account ${subject} requires manual cleanup`); }
    }
    throw error;
  } finally {
    await db.query("SELECT pg_advisory_unlock(hashtext('siga-official-demo-bootstrap'))");
  }
}
