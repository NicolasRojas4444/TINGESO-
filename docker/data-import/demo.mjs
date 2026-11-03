import { inTransaction } from "./catalog.mjs";

export const demoAccounts = [
  { username: "admin", role: "ADMIN", run: "90000001-4", name: "Demo Admin", email: "demo-admin@siga.example.test" },
  { username: "teacher", role: "TEACHER", run: "90000002-2", name: "Demo Teacher", email: "demo-teacher@siga.example.test" },
  { username: "student", role: "STUDENT", run: "90000003-0", name: "Demo Student", email: "demo-student@siga.example.test" },
];
const programCode = "DEMO-SIGA";
const description = "Explicit SIGA demo scenario; not official academic data.";

export function requireDemoSetup(env) {
  if (env.DEMO_SETUP_ENABLED !== "true") throw new Error("Set DEMO_SETUP_ENABLED=true explicitly to prepare demo data");
  if (!env.DEMO_PASSWORD || env.DEMO_PASSWORD.length < 12 || env.DEMO_PASSWORD.length > 128 || !env.DEMO_PASSWORD.trim())
    throw new Error("DEMO_PASSWORD must contain 12 to 128 characters");
}

export async function prepareDemo(db, keycloak, password) {
  const created = [];
  try {
    return await inTransaction(db, async () => {
      await db.query("SELECT pg_advisory_xact_lock(hashtext('siga-explicit-demo'))");
      const existing = (await db.query("SELECT * FROM degree_programs WHERE code=$1 FOR UPDATE", [programCode])).rows[0];
      if (existing) {
        if (existing.description !== description) throw new Error("DEMO-SIGA is already in use by unrelated data; nothing was overwritten");
        for (const person of demoAccounts) {
          const account = await keycloak.findUser(person.username);
          const local = (await db.query("SELECT * FROM app_users WHERE run=$1 AND email=$2", [person.run, person.email])).rows[0];
          if (!account || !local || local.keycloak_subject !== account.id)
            throw new Error("Demo setup is incomplete or an identity was changed; manual review is required, no passwords were reset");
          if (!account.enabled || local.access_suspended)
            throw new Error("A demo account is disabled or suspended; use normal account recovery instead");
          const roles = await keycloak.request("GET", `/users/${account.id}/role-mappings/realm/composite`);
          if (!roles.some(role => role.name === person.role) || roles.some(role => ["ADMIN", "TEACHER", "STUDENT"].includes(role.name) && role.name !== person.role))
            throw new Error("A demo account's academic roles changed; no privileges were modified");
        }
        const complete = (await db.query(`SELECT
          (SELECT count(*) FROM courses c JOIN study_plans p ON p.id=c.study_plan_id WHERE p.degree_program_id=$1)=7
          AND EXISTS(SELECT 1 FROM enrollments e JOIN students s ON s.id=e.student_id JOIN app_users u ON u.id=s.user_id
            JOIN academic_periods a ON a.id=e.academic_period_id WHERE u.run=$2 AND a.academic_year=2099 AND a.semester=1)
          AND (SELECT count(*) FROM sections s JOIN courses c ON c.id=s.course_id JOIN study_plans p ON p.id=c.study_plan_id
            WHERE p.degree_program_id=$1)=7 AS ready`, [existing.id, demoAccounts[2].run])).rows[0].ready;
        if (!complete) throw new Error("Demo academic data changed or is incomplete; existing data was preserved");
        return { existing: true };
      }

      // Never adopt existing accounts, reset passwords, reuse email/RUN identities,
      // or attach demo sections to a pre-existing academic period.
      if ((await db.query("SELECT 1 FROM academic_periods WHERE academic_year=2099 AND semester=1")).rowCount)
        throw new Error("Period 2099-1 already exists; no existing period was changed");
      const roles = new Map();
      for (const person of demoAccounts) {
        if (await keycloak.findUser(person.username)) throw new Error(`Reserved demo username ${person.username} already exists; no account was changed`);
        if ((await keycloak.request("GET", `/users?email=${encodeURIComponent(person.email)}&exact=true`)).length)
          throw new Error("A reserved demo email is already in use; no account was changed");
        if ((await db.query("SELECT 1 FROM app_users WHERE run=$1 OR lower(email)=lower($2)", [person.run, person.email])).rowCount)
          throw new Error("A reserved demo identity is already in use; no identity was relinked");
        roles.set(person.role, await keycloak.request("GET", `/roles/${person.role}`));
      }

      const insert = async (sql, values = []) => (await db.query(sql + " RETURNING id", values)).rows[0].id;
      const program = await insert("INSERT INTO degree_programs(row_version,code,name,description,duration_semesters,status) VALUES (0,$1,'DEMO - Pruebas SIGA IPT',$2,4,'ACTIVE')", [programCode, description]);
      const plan = await insert("INSERT INTO study_plans(row_version,degree_program_id,plan_version,status) VALUES (0,$1,'DEMO-1','CURRENT')", [program]);
      const period = await insert("INSERT INTO academic_periods(row_version,academic_year,semester,status) VALUES (0,2099,1,'OPEN')");
      const identities = new Map();
      for (const person of demoAccounts) {
        const response = await keycloak.request("POST", "/users", {
          username: person.username, email: person.email, firstName: "Demo", lastName: person.role,
          enabled: false, requiredActions: [], emailVerified: false,
          credentials: [{ type: "password", temporary: false, value: password }],
        });
        const location = response.headers.get("location");
        if (!location) throw new Error("Keycloak returned no account ID; manual review is required");
        const subject = location.split("/").pop();
        created.push(subject);
        await keycloak.request("POST", `/users/${subject}/role-mappings/realm`, [roles.get(person.role)]);
        const user = await insert("INSERT INTO app_users(row_version,keycloak_subject,run,full_name,email,access_suspended) VALUES (0,$1,$2,$3,$4,false)", [subject, person.run, person.name, person.email]);
        identities.set(person.role, user);
      }
      const teacher = await insert("INSERT INTO teachers(row_version,user_id,professional_title,academic_degree,status) VALUES (0,$1,'Demo Engineer','Demo Bachelor','ACTIVE')", [identities.get("TEACHER")]);
      const student = await insert("INSERT INTO students(row_version,user_id,study_plan_id,academic_status) VALUES (0,$1,$2,'REGULAR')", [identities.get("STUDENT"), plan]);
      await db.query("INSERT INTO enrollments(row_version,student_id,academic_period_id,enrolled_at) VALUES (0,$1,$2,now())", [student, period]);
      const courses = [];
      for (let index = 0; index < 7; index++) {
        const course = await insert("INSERT INTO courses(row_version,study_plan_id,code,name,semester,theory_hours,exercise_hours,laboratory_hours,sct) VALUES (0,$1,$2,$3,$4,2,0,0,4)", [plan, `DEMO${index + 1}`, `DEMO - Asignatura ${index + 1}`, index < 6 ? 1 : 2]);
        courses.push(course);
        const section = await insert("INSERT INTO sections(row_version,course_id,academic_period_id,teacher_id,capacity) VALUES (0,$1,$2,$3,30)", [course, period, teacher]);
        await db.query("INSERT INTO section_schedule_blocks(row_version,section_id,day_of_week,module) VALUES (0,$1,$2,$3)", [section, index < 6 ? "MONDAY" : "TUESDAY", index < 6 ? index + 1 : 1]);
      }
      await db.query("INSERT INTO course_prerequisites(course_id,prerequisite_id,row_version) VALUES ($1,$2,0)", [courses[6], courses[0]]);
      for (const subject of created) await keycloak.request("PUT", `/users/${subject}`, { enabled: true });
      return { existing: false };
    });
  } catch (error) {
    for (const subject of created) {
      try { await keycloak.request("DELETE", `/users/${subject}`); }
      catch { console.error(`Incomplete demo account ${subject} requires manual cleanup`); }
    }
    throw error;
  }
}
