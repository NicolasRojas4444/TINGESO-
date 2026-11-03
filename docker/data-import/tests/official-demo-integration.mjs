import assert from "node:assert/strict";
import pg from "pg";
import { KeycloakAdmin } from "../keycloak.mjs";
import { initializeOfficialDemo } from "../official-demo.mjs";

if (process.env.ISOLATED_OFFICIAL_DEMO_TEST !== "true" || process.env.PGDATABASE !== "siga_official_demo_test")
  throw new Error("Disposable official demo database required");
const db = new pg.Client();
const kc = new KeycloakAdmin(process.env);
const password = process.env.DEMO_PASSWORD;
let clientId;
await db.connect();
try {
  const count = async table => (await db.query(`SELECT count(*)::int AS n FROM ${table}`)).rows[0].n;
  assert.equal(await count("courses"), 72);
  assert.equal(await count("course_prerequisites"), 75);
  assert.equal(await count("degree_programs"), 3);
  assert.equal(await count("sections"), 7);
  assert.equal(await count("section_schedule_blocks"), 16);
  assert.equal(await count("course_registrations"), 0);
  assert.equal(await count("app_seed_state"), 1);
  const versions = (await db.query("SELECT plan_version FROM study_plans ORDER BY degree_program_id")).rows.map(p => p.plan_version);
  assert.deepEqual(versions, ["2021.3", "2022.3", "2021.3"]);
  const response = await kc.request("POST", "/clients", {
    clientId: "official-demo-isolated-login", publicClient: true, standardFlowEnabled: false,
    directAccessGrantsEnabled: true, defaultClientScopes: ["basic", "roles", "profile", "email"],
  });
  clientId = response.headers.get("location").split("/").pop();
  async function login(username) {
    const response = await fetch(`${kc.base}/realms/siga/protocol/openid-connect/token`, {
      method: "POST", body: new URLSearchParams({ client_id: "official-demo-isolated-login", grant_type: "password", username, password }),
    });
    assert.equal(response.status, 200, `Login ${username}`);
    return (await response.json()).access_token;
  }
  async function api(path, token, method = "GET", body, status = 200) {
    const response = await fetch("http://backend:8080/api" + path, {
      method, headers: { Authorization: `Bearer ${token}`, ...(body ? { "Content-Type": "application/json" } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    assert.equal(response.status, status, `${method} ${path}`);
    return response.json();
  }
  const admin = await login("admin");
  const teacher = await login("teacher");
  const student = await login("student");
  const profile = await api("/students/me", student);
  const plan = (await db.query("SELECT p.id FROM study_plans p JOIN degree_programs d ON d.id=p.degree_program_id WHERE d.code='10450'")).rows[0];
  assert.equal(String(profile.studyPlanId), String(plan.id));
  await api("/teachers/me", teacher);
  await api("/students", student, "GET", undefined, 403);
  const period = (await api("/academic-periods", admin)).find(p => p.year === 2099 && p.semester === 2);
  const sections = (await db.query("SELECT s.id,c.code FROM sections s JOIN courses c ON c.id=s.course_id ORDER BY c.code")).rows;
  assert.equal(sections[6].code, "TAP201");
  await api(`/registrations?studentId=${profile.id}&periodId=${period.id}`, student, "POST", [{sectionId:sections[6].id}], 409);
  const registrations = await api(`/registrations?studentId=${profile.id}&periodId=${period.id}`, student, "POST", sections.slice(0,6).map(s => ({sectionId:s.id})), 201);
  await api(`/academic-periods/${period.id}/close`, admin, "POST", undefined, 409);
  for (const registration of registrations)
    await api(`/final-grades/by-registration/${registration.id}`, teacher, "POST", {value:5.5}, 201);
  await api(`/academic-periods/${period.id}/close`, admin, "POST");
  assert.equal((await api(`/students/${profile.id}/history`, student)).length, 6);
  await db.query("UPDATE courses SET name='User edited course' WHERE code='TAP101'");
  assert.deepEqual(await initializeOfficialDemo(db, {}, "not-applied"), { existing: true });
  assert.equal((await db.query("SELECT name FROM courses WHERE code='TAP101'")).rows[0].name, "User edited course");
  assert.equal((await db.query("SELECT status FROM academic_periods WHERE id=$1", [period.id])).rows[0].status, "CLOSED");
  await login("admin");
  assert.equal(await count("course_registrations"), 6);
  console.log("PASS: automatic official catalog (72/75), 3 real logins, valid TEL offering/enrollment, registration, grades, closing/history, and preservation of user edits/progress on repeated initialization.");
} finally {
  if (clientId) await kc.request("DELETE", `/clients/${clientId}`);
  await db.end();
}
