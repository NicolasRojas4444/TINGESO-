import assert from "node:assert/strict";
import pg from "pg";
import { loadCatalog, importCatalog } from "../catalog.mjs";
import {
  accountDefinitions,
  prepareAccounts,
  requireLocalSetup,
} from "../accounts.mjs";
import { KeycloakAdmin } from "../keycloak.mjs";

requireLocalSetup(process.env, "all");
if (process.env.ISOLATED_DATA_IMPORT_TEST !== "true")
  throw new Error("This fixture is restricted to the isolated test runner");
const db = new pg.Client();
const kc = new KeycloakAdmin(process.env);
const password = process.env.LOCAL_TEST_PASSWORD;
const otherPassword = "isolated-sentinel-password";
const catalog = await loadCatalog();
let loginClient;
const log = (message) => console.log(`PASS: ${message}`);
await db.connect();
try {
  assert.equal(
    (await db.query("SELECT count(*)::int AS total FROM app_users")).rows[0]
      .total,
    0,
    "A disposable empty application database is required",
  );
  const {
    rows: [demo],
  } = await db.query(
    "INSERT INTO degree_programs(code,name,description,duration_semesters,status,row_version) VALUES ('PRESERVE','Preserve existing data','Test fixture',4,'ACTIVE',0) RETURNING id",
  );
  const {
    rows: [oldPlan],
  } = await db.query(
    "INSERT INTO study_plans(degree_program_id,plan_version,status,row_version) VALUES ($1,'OLD','CURRENT',0) RETURNING id",
    [demo.id],
  );
  const {
    rows: [period],
  } = await db.query(
    "INSERT INTO academic_periods(academic_year,semester,status,row_version) VALUES (2040,1,'OPEN',0) RETURNING id",
  );
  const original = new Map();
  for (const person of accountDefinitions) {
    if (person.role === "ADMIN") continue;
    const response = await kc.request("POST", "/users", {
      username: person.username,
      email: person.email,
      firstName: "Test",
      lastName: person.role,
      enabled: true,
      credentials: [
        { type: "password", temporary: false, value: otherPassword },
      ],
    });
    const id = response.headers.get("location").split("/").pop();
    const role = await kc.request("GET", `/roles/${person.role}`);
    await kc.request("POST", `/users/${id}/role-mappings/realm`, [role]);
    const {
      rows: [local],
    } = await db.query(
      "INSERT INTO app_users(keycloak_subject,run,full_name,email,access_suspended,row_version) VALUES ($1,$2,$3,$4,false,0) RETURNING id",
      [id, person.run, person.name, person.email],
    );
    original.set(person.username, { subject: id, userId: local.id });
    if (person.role === "TEACHER")
      await db.query(
        "INSERT INTO teachers(user_id,professional_title,academic_degree,status,row_version) VALUES ($1,'Engineer','Bachelor','ACTIVE',0)",
        [local.id],
      );
    if (person.role === "STUDENT") {
      const {
        rows: [student],
      } = await db.query(
        "INSERT INTO students(user_id,study_plan_id,academic_status,row_version) VALUES ($1,$2,'REGULAR',0) RETURNING id",
        [local.id, oldPlan.id],
      );
      original.get(person.username).studentId = student.id;
      await db.query(
        "INSERT INTO enrollments(student_id,academic_period_id,enrolled_at,row_version) VALUES ($1,$2,now(),0)",
        [student.id, period.id],
      );
    }
  }
  await kc.request("POST", "/users", {
    username: "untouched",
    email: "untouched@example.test",
    firstName: "Untouched",
    lastName: "Account",
    enabled: true,
    credentials: [{ type: "password", temporary: false, value: otherPassword }],
  });
  const legacyResponse = await kc.request("POST", "/users", {
    username: "academic-admin",
    email: "admin@example.test",
    firstName: "Legacy",
    lastName: "Admin",
    enabled: true,
    credentials: [{ type: "password", temporary: false, value: otherPassword }],
  });
  const legacyId = legacyResponse.headers.get("location").split("/").pop();
  const legacyRole = await kc.request("GET", "/roles/ADMIN");
  await kc.request("POST", `/users/${legacyId}/role-mappings/realm`, [
    legacyRole,
  ]);
  await db.query(
    "INSERT INTO app_users(keycloak_subject,run,full_name,email,access_suspended,row_version) VALUES ($1,'11111111-1','Legacy Admin','admin@example.test',false,0)",
    [legacyId],
  );

  const plans = await importCatalog(db, catalog);
  await prepareAccounts(db, kc, plans.get("10450"), password, () => {});
  assert.equal((await kc.findUser("academic-admin")).id, legacyId);
  const newAdmin = await kc.findUser("admin");
  assert.notEqual(newAdmin.id, legacyId);
  original.set("admin", {
    subject: newAdmin.id,
    userId: (
      await db.query("SELECT id FROM app_users WHERE keycloak_subject=$1", [
        newAdmin.id,
      ])
    ).rows[0].id,
  });
  for (const person of accountDefinitions)
    assert.equal(
      (await kc.findUser(person.username)).id,
      original.get(person.username).subject,
    );
  const migrated = (
    await db.query("SELECT study_plan_id FROM students WHERE id=$1", [
      original.get("student").studentId,
    ])
  ).rows[0];
  assert.equal(String(migrated.study_plan_id), String(plans.get("10450")));
  assert.equal(
    (await db.query("SELECT count(*)::int AS total FROM enrollments")).rows[0]
      .total,
    1,
  );
  assert.equal(
    (await db.query("SELECT count(*)::int AS total FROM sections")).rows[0]
      .total,
    0,
  );
  log(
    "legacy admin preserved; simple admin created separately; existing teacher/student IDs and enrollment preserved; no sections fabricated",
  );

  await importCatalog(db, catalog);
  await prepareAccounts(db, kc, plans.get("10450"), password, () => {});
  const totals = (
    await db.query(
      "SELECT (SELECT count(*)::int FROM courses) AS courses, (SELECT count(*)::int FROM app_users) AS users, (SELECT count(*)::int FROM students) AS students, (SELECT count(*)::int FROM teachers) AS teachers",
    )
  ).rows[0];
  assert.deepEqual(totals, { courses: 72, users: 4, students: 1, teachers: 1 });
  for (const person of accountDefinitions)
    assert.equal(
      (await kc.findUser(person.username)).id,
      original.get(person.username).subject,
    );
  for (const planId of plans.values()) {
    const counts = (
      await db.query(
        "SELECT semester,count(*)::int AS total FROM courses WHERE study_plan_id=$1 GROUP BY semester ORDER BY semester",
        [planId],
      )
    ).rows;
    assert.deepEqual(
      counts.map((row) => row.total),
      [6, 6, 6, 6],
    );
  }
  const expectedLinks = catalog.programs
    .flatMap((program) => program.courses)
    .reduce((total, course) => total + course.prerequisites.length, 0);
  assert.equal(
    (await db.query("SELECT count(*)::int AS total FROM course_prerequisites"))
      .rows[0].total,
    expectedLinks,
  );
  log(
    "repeat imports are idempotent: 72 courses, all prerequisite links, four complete semesters per official plan",
  );

  // A short-lived login client exists only in this disposable test realm.
  const clientName = `import-check-${Date.now()}`;
  const response = await kc.request("POST", "/clients", {
    clientId: clientName,
    publicClient: true,
    directAccessGrantsEnabled: true,
    standardFlowEnabled: false,
    defaultClientScopes: ["basic", "roles", "profile", "email"],
  });
  loginClient = response.headers.get("location").split("/").pop();
  async function login(username, value) {
    const result = await fetch(
      `${process.env.KEYCLOAK_SERVER_URL}/realms/siga/protocol/openid-connect/token`,
      {
        method: "POST",
        body: new URLSearchParams({
          client_id: clientName,
          grant_type: "password",
          username,
          password: value,
        }),
      },
    );
    assert.equal(result.status, 200, `Keycloak login failed for ${username}`);
    return (await result.json()).access_token;
  }
  async function api(path, token) {
    const response = await fetch(`http://backend:8080/api${path}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    assert.equal(response.status, 200, `Backend request failed: ${path}`);
    return response.json();
  }
  const adminToken = await login("admin", password);
  assert.equal(
    (await api("/degree-programs", adminToken)).filter((program) =>
      ["10450", "10451", "10452"].includes(program.code),
    ).length,
    3,
  );
  await api("/teachers/me", await login("teacher", password));
  const studentToken = await login("student", password);
  const profile = await api("/students/me", studentToken);
  assert.equal(String(profile.studyPlanId), String(plans.get("10450")));
  assert.equal(
    (await api(`/courses/by-study-plan/${profile.studyPlanId}`, studentToken))
      .length,
    24,
  );
  await login("untouched", otherPassword);
  await login("academic-admin", otherPassword);
  log(
    "all three selected credentials work against Keycloak and real backend endpoints; another account's password is unchanged",
  );

  await db.query(
    "UPDATE courses SET name='User-edited course' WHERE code='TAP101'",
  );
  await assert.rejects(
    () => importCatalog(db, catalog),
    /differs from Appendix A/,
  );
  assert.equal(
    (await db.query("SELECT name FROM courses WHERE code='TAP101'")).rows[0]
      .name,
    "User-edited course",
  );
  log("conflicting catalog data is rejected rather than overwritten");

  const studentId = original.get("student").studentId;
  const teacherId = (
    await db.query("SELECT id FROM teachers WHERE user_id=$1", [
      original.get("teacher").userId,
    ])
  ).rows[0].id;
  await db.query("UPDATE students SET study_plan_id=$1 WHERE id=$2", [
    oldPlan.id,
    studentId,
  ]);
  const {
    rows: [course],
  } = await db.query(
    "INSERT INTO courses(study_plan_id,code,name,semester,theory_hours,exercise_hours,laboratory_hours,sct,row_version) VALUES ($1,'PRESERVE1','Existing academic work',1,2,0,0,4,0) RETURNING id",
    [oldPlan.id],
  );
  const {
    rows: [section],
  } = await db.query(
    "INSERT INTO sections(course_id,academic_period_id,teacher_id,capacity,row_version) VALUES ($1,$2,$3,30,0) RETURNING id",
    [course.id, period.id, teacherId],
  );
  await db.query(
    "INSERT INTO course_registrations(student_id,section_id,registered_at,status,row_version) VALUES ($1,$2,now(),'ACTIVE',0)",
    [studentId, section.id],
  );
  await kc.request(
    "PUT",
    `/users/${original.get("admin").subject}/reset-password`,
    { type: "password", temporary: false, value: otherPassword },
  );
  await assert.rejects(
    () => prepareAccounts(db, kc, plans.get("10450"), password, () => {}),
    /academic activity/,
  );
  await login("admin", otherPassword);
  assert.equal(
    String(
      (
        await db.query("SELECT study_plan_id FROM students WHERE id=$1", [
          studentId,
        ])
      ).rows[0].study_plan_id,
    ),
    String(oldPlan.id),
  );
  log(
    "student activity blocks plan replacement before any passwords are changed",
  );
} finally {
  if (loginClient) await kc.request("DELETE", `/clients/${loginClient}`);
  await db.end();
}
