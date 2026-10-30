import { test, expect } from "@playwright/test";
import pg from "pg";

test.describe.configure({ mode: "serial" });
const suffix = Date.now().toString();
const password = `E2e-${suffix}-only!`;
const username = (role) => `ui-${role}-${suffix}`;
const kc = process.env.E2E_KEYCLOAK_URL;
const backend = process.env.E2E_BACKEND_URL;
let adminToken, teacher, student, program, plan, period;
const courses = [],
  sections = [];

async function checked(response, status = 200) {
  const body = await response.text();
  expect(response.status(), body).toBe(status);
  return body ? JSON.parse(body) : undefined;
}
async function api(request, method, path, data, status = 200) {
  return checked(
    await request.fetch(`${backend}/api${path}`, {
      method,
      data,
      headers: { Authorization: `Bearer ${adminToken}` },
    }),
    status,
  );
}
async function login(page, role) {
  await page.goto("/");
  await expect(
    page.getByRole("button", { name: "Iniciar sesión con Keycloak" }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Iniciar sesión con Keycloak" })
    .click();
  await page.locator("#username").fill(username(role));
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();
  await expect(
    page.getByRole("button", { name: "Cerrar sesión" }),
  ).toBeVisible();
}

test.beforeAll(async ({ request }) => {
  if (!kc || !backend || !process.env.E2E_DATABASE_URL)
    throw new Error("Run this test only with the isolated Docker runner.");
  const master = await checked(
    await request.post(`${kc}/realms/master/protocol/openid-connect/token`, {
      form: {
        client_id: "admin-cli",
        grant_type: "password",
        username: process.env.KEYCLOAK_ADMIN,
        password: process.env.KEYCLOAK_ADMIN_PASSWORD,
      },
    }),
  );
  const headers = { Authorization: `Bearer ${master.access_token}` };
  await checked(
    await request.post(`${kc}/admin/realms/siga/clients`, {
      headers,
      data: {
        clientId: `ui-login-${suffix}`,
        publicClient: true,
        directAccessGrantsEnabled: true,
        standardFlowEnabled: false,
        defaultClientScopes: ["basic", "roles", "profile", "email"],
      },
    }),
    201,
  );
  await checked(
    await request.post(`${kc}/admin/realms/siga/users`, {
      headers,
      data: {
        username: username("admin"),
        enabled: true,
        email: `ui-admin-${suffix}@example.test`,
        firstName: "UI",
        lastName: "Admin",
        credentials: [{ type: "password", value: password, temporary: false }],
      },
    }),
    201,
  );
  const users = await checked(
    await request.get(
      `${kc}/admin/realms/siga/users?username=${username("admin")}&exact=true`,
      { headers },
    ),
  );
  const role = await checked(
    await request.get(`${kc}/admin/realms/siga/roles/ADMIN`, { headers }),
  );
  await checked(
    await request.post(
      `${kc}/admin/realms/siga/users/${users[0].id}/role-mappings/realm`,
      { headers, data: [role] },
    ),
    204,
  );
  const token = await checked(
    await request.post(`${kc}/realms/siga/protocol/openid-connect/token`, {
      form: {
        client_id: `ui-login-${suffix}`,
        grant_type: "password",
        username: username("admin"),
        password,
      },
    }),
  );
  adminToken = token.access_token;
  program = await api(
    request,
    "POST",
    "/degree-programs",
    {
      code: `UI${suffix}`,
      name: "UI Test Program",
      description: "Isolated browser-test fixture",
    },
    201,
  );
  plan = await api(
    request,
    "POST",
    "/study-plans",
    { degreeProgramId: program.id, version: "UI-V1", status: "CURRENT" },
    201,
  );
  period = await api(
    request,
    "POST",
    "/academic-periods",
    { year: 2035, semester: 1 },
    201,
  );
  teacher = await checked(
    await request.post(`${backend}/api/auth/register`, {
      data: {
        username: username("teacher"),
        password,
        run: "95555555-0",
        fullName: "UI Teacher",
        email: `ui-teacher-${suffix}@example.test`,
        role: "TEACHER",
        professionalTitle: "Engineer",
        academicDegree: "Bachelor",
      },
    }),
    201,
  );
  student = await checked(
    await request.post(`${backend}/api/auth/register`, {
      data: {
        username: username("student"),
        password,
        run: "96666666-0",
        fullName: "UI Student",
        email: `ui-student-${suffix}@example.test`,
        role: "STUDENT",
        degreeProgramId: program.id,
      },
    }),
    201,
  );
  for (let index = 1; index <= 4; index++) {
    const course = await api(
      request,
      "POST",
      "/courses",
      {
        studyPlanId: plan.id,
        code: `UI${index}`,
        name: `UI Course ${index}`,
        semester: 1,
        theoryHours: 2,
        exerciseHours: 0,
        laboratoryHours: 0,
        sct: 4,
      },
      201,
    );
    courses.push(course);
    sections.push(
      await api(
        request,
        "POST",
        "/sections",
        {
          courseId: course.id,
          academicPeriodId: period.id,
          teacherId: teacher.teacherId,
          capacity: 30,
          scheduleBlocks: [{ dayOfWeek: "MONDAY", module: index }],
        },
        201,
      ),
    );
  }
});

test("ADMIN navigates catalogs and creates and edits real records", async ({
  page,
}) => {
  await login(page, "admin");
  await expect(page).toHaveURL(/\/admin\/students$/);
  await expect(page.getByRole("row", { name: /UI Student/ })).toBeVisible();
  await page.getByRole("link", { name: "Ver detalle", exact: true }).click();
  await expect(page.getByRole("heading", { name: "UI Student" })).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Historial académico" }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Carreras y planes", exact: true })
    .click();
  await page.getByRole("button", { name: "Nueva carrera" }).click();
  await page.getByLabel("Código", { exact: true }).fill("UI-CRUD");
  await page
    .getByLabel("Nombre", { exact: true })
    .fill("Browser-created program");
  await page
    .getByLabel("Descripción", { exact: true })
    .fill("Created through the actual form");
  await page.getByRole("button", { name: "Guardar", exact: true }).click();
  const row = page.getByRole("row", { name: /UI-CRUD/ });
  await expect(row).toBeVisible();
  await row.getByRole("button", { name: "Editar", exact: true }).click();
  await page
    .getByLabel("Nombre", { exact: true })
    .fill("Browser-edited program");
  await page.getByRole("button", { name: "Guardar", exact: true }).click();
  await expect(row).toContainText("Browser-edited program");
  await page.goto(`/admin/programs/${program.id}`);
  await expect(page.getByText("UI-V1", { exact: true })).toBeVisible();
  await page
    .getByRole("link", { name: "Ver asignaturas", exact: true })
    .click();
  await expect(
    page.getByRole("row", { name: /UI1 UI Course 1/ }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Prerrequisitos", exact: true })
    .first()
    .click();
  await page.getByRole("button", { name: "Guardar", exact: true }).click();
  await page.getByRole("link", { name: "Docentes", exact: true }).click();
  await expect(page.getByRole("row", { name: /UI Teacher/ })).toBeVisible();
});

test("STUDENT sees enrollment rejection, then registers, cancels and replaces courses", async ({
  page,
}) => {
  await login(page, "student");
  await expect(
    page.getByRole("heading", { name: "Mi información", exact: true }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Mi plan de estudios", exact: true })
    .click();
  await expect(
    page.getByRole("row", { name: /UI1 UI Course 1/ }),
  ).toBeVisible();
  await page
    .getByRole("link", { name: "Inscripción académica", exact: true })
    .click();
  for (const index of [1, 2, 3, 4])
    await page
      .getByRole("checkbox", {
        name: `Seleccionar UI Course ${index}`,
        exact: true,
      })
      .check();
  await page.getByRole("button", { name: "Guardar selección" }).click();
  await expect(page.getByRole("alert")).toContainText("not enrolled");
  const database = new pg.Client({
    connectionString: process.env.E2E_DATABASE_URL,
  });
  await database.connect();
  try {
    await database.query(
      "INSERT INTO enrollments(student_id,academic_period_id,enrolled_at,row_version) VALUES ($1,$2,CURRENT_TIMESTAMP,0)",
      [student.studentId, period.id],
    );
  } finally {
    await database.end();
  }
  await page.getByRole("button", { name: "Guardar selección" }).click();
  await expect(
    page.getByRole("heading", { name: "Inscripciones actuales" }),
  ).toBeVisible();
  const current = page.locator("section").filter({
    has: page.getByRole("heading", { name: "Inscripciones actuales" }),
  });
  page.once("dialog", (dialog) => dialog.accept());
  await current
    .getByRole("row", { name: /UI Course 1/ })
    .getByRole("button", { name: "Anular inscripción" })
    .click();
  await expect(
    page.getByRole("heading", { name: "Selección: 3 asignaturas" }),
  ).toBeVisible();
  await page
    .getByRole("checkbox", { name: "Seleccionar UI Course 2", exact: true })
    .uncheck();
  await page
    .getByRole("checkbox", { name: "Seleccionar UI Course 1", exact: true })
    .check();
  await page.getByRole("button", { name: "Guardar selección" }).click();
  await expect(current.getByRole("row", { name: /UI Course 1/ })).toBeVisible();
  await expect(current.getByRole("row", { name: /UI Course 2/ })).toHaveCount(
    0,
  );
  await page.goto("/admin/students");
  await expect(
    page.getByRole("heading", { name: "Acceso restringido" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Nuevo estudiante" }),
  ).toHaveCount(0);
});

test("ADMIN sees the structured rejection when closing without grades", async ({
  page,
}) => {
  await login(page, "admin");
  await page.goto(`/admin/periods/${period.id}`);
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .getByRole("button", { name: "Cerrar período", exact: true })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "Faltan notas finales en las secciones:",
  );
  await expect(
    page.getByRole("button", { name: "Nueva sección" }),
  ).toBeVisible();
});

test("TEACHER records and updates grades only in assigned sections", async ({
  page,
}) => {
  await login(page, "teacher");
  await expect(page).toHaveURL(/\/teacher\/sections$/);
  await expect(page.getByRole("row", { name: /UI Course 1/ })).toBeVisible();
  for (const index of [0, 2, 3]) {
    await page.goto(`/sections/${sections[index].id}`);
    await page
      .getByRole("spinbutton", { name: "Nota de UI Student" })
      .fill("6.0");
    await page
      .getByRole("button", { name: "Guardar nota", exact: true })
      .click();
    await expect(
      page.getByText("Nota guardada.", { exact: true }),
    ).toBeVisible();
  }
  await page
    .getByRole("spinbutton", { name: "Nota de UI Student" })
    .fill("6.5");
  await page
    .getByRole("button", { name: "Actualizar nota", exact: true })
    .click();
  await expect(
    page.getByRole("button", { name: "Actualizar nota", exact: true }),
  ).toBeEnabled();
  await page.goto("/student/profile");
  await expect(
    page.getByRole("heading", { name: "Acceso restringido" }),
  ).toBeVisible();
});

test("ADMIN closes the period and STUDENT reads consolidated history", async ({
  page,
  browser,
}) => {
  await login(page, "admin");
  await page.goto(`/sections/${sections[0].id}`);
  await expect(page.getByRole("spinbutton")).toHaveCount(0);
  await page.goto(`/admin/periods/${period.id}`);
  page.once("dialog", (dialog) => dialog.accept());
  await page
    .getByRole("button", { name: "Cerrar período", exact: true })
    .click();
  await expect(
    page.getByText("Este período está cerrado:", { exact: false }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Nueva sección" })).toHaveCount(
    0,
  );
  const context = await browser.newContext({
    baseURL: process.env.E2E_FRONTEND_URL,
  });
  try {
    const studentPage = await context.newPage();
    await login(studentPage, "student");
    await studentPage
      .getByRole("link", { name: "Historial académico", exact: true })
      .click();
    await expect(
      studentPage.getByRole("row", { name: /UI1 UI Course 1/ }),
    ).toContainText("6.0");
    await expect(
      studentPage.getByRole("row", { name: /UI4 UI Course 4/ }),
    ).toContainText("6.5");
    await studentPage.getByRole("button", { name: "Cerrar sesión" }).click();
    await expect(
      studentPage.getByRole("button", { name: "Iniciar sesión con Keycloak" }),
    ).toBeVisible();
  } finally {
    await context.close();
  }
});
