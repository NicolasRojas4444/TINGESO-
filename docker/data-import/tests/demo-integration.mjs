import assert from "node:assert/strict";
import pg from "pg";
import { KeycloakAdmin } from "../keycloak.mjs";
import { prepareDemo, requireDemoSetup } from "../demo.mjs";

if (process.env.ISOLATED_DEMO_TEST !== "true") throw new Error("Disposable demo test only");
requireDemoSetup(process.env);
const db = new pg.Client();
const kc = new KeycloakAdmin(process.env);
const password = process.env.DEMO_PASSWORD;
let clientId;
await db.connect();
try {
  assert.equal((await db.query("SELECT count(*)::int AS n FROM app_users")).rows[0].n, 0);
  await db.query("INSERT INTO degree_programs(row_version,code,name,description,duration_semesters,status) VALUES (0,'PRESERVE','Keep me','Unrelated data',4,'ACTIVE')");
  assert.equal((await prepareDemo(db, kc, password)).existing, false);
  assert.equal((await prepareDemo(db, kc, "different-password-not-applied")).existing, true);
  assert.equal((await db.query("SELECT count(*)::int AS n FROM sections")).rows[0].n, 7);
  assert.equal((await db.query("SELECT count(*)::int AS n FROM degree_programs WHERE code='PRESERVE'")).rows[0].n, 1);
  const response = await kc.request("POST", "/clients", {
    clientId: "demo-isolated-login", publicClient: true, standardFlowEnabled: false,
    directAccessGrantsEnabled: true, defaultClientScopes: ["basic", "roles", "profile", "email"],
  });
  clientId = response.headers.get("location").split("/").pop();
  async function login(username) {
    const result = await fetch(`${kc.base}/realms/siga/protocol/openid-connect/token`, {
      method: "POST", body: new URLSearchParams({ client_id: "demo-isolated-login", grant_type: "password", username, password }),
    });
    assert.equal(result.status, 200, `Login ${username}`);
    return (await result.json()).access_token;
  }
  async function api(path, token, method = "GET", body, status = 200) {
    const result = await fetch(`http://backend:8080/api${path}`, {
      method, headers: { Authorization: `Bearer ${token}`, ...(body ? { "Content-Type": "application/json" } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    assert.equal(result.status, status, `${method} ${path}`);
    return result.json();
  }
  const admin = await login("admin");
  const teacher = await login("teacher");
  const student = await login("student");
  const profile = await api("/students/me", student);
  assert.equal(profile.academicStatus, "REGULAR");
  assert.equal((await api("/teachers/me", teacher)).status, "ACTIVE");
  await api("/students", teacher, "GET", undefined, 403);
  await api("/students", student, "GET", undefined, 403);
  const period = (await api("/academic-periods", admin)).find(p => p.year === 2099 && p.semester === 1);
  const sections = (await db.query("SELECT s.id,c.semester FROM sections s JOIN courses c ON c.id=s.course_id ORDER BY c.code")).rows;
  await api(`/registrations?studentId=${profile.id}&periodId=${period.id}`, student, "POST", [{sectionId:sections[6].id}], 409);
  const registrations = await api(`/registrations?studentId=${profile.id}&periodId=${period.id}`, student, "POST", sections.slice(0,6).map(s => ({sectionId:s.id})), 201);
  await api(`/academic-periods/${period.id}/close`, admin, "POST", undefined, 409);
  for (const registration of registrations)
    await api(`/final-grades/by-registration/${registration.id}`, teacher, "POST", {value:5.5}, 201);
  await api(`/academic-periods/${period.id}/close`, admin, "POST");
  assert.equal((await api(`/students/${profile.id}/history`, student)).length, 6);
  assert.equal((await prepareDemo(db, kc, "different-password-not-applied")).existing, true);
  assert.equal((await db.query("SELECT status FROM academic_periods WHERE id=$1", [period.id])).rows[0].status, "CLOSED");
  await login("admin");
  assert.equal((await db.query("SELECT count(*)::int AS n FROM course_registrations")).rows[0].n, 6);
  console.log("PASS: real demo logins/roles, enrollment, prerequisite rejection, six registrations, teacher grades, rejected/accepted closing, six history records, unchanged passwords and progress on rerun, unrelated data preserved.");
} finally {
  if (clientId) await kc.request("DELETE", `/clients/${clientId}`);
  await db.end();
}
