import { inTransaction } from "./catalog.mjs";

export const accountDefinitions = [
  {
    username: "admin",
    role: "ADMIN",
    run: "10000001-6",
    name: "Local Admin",
    email: "local-admin@example.test",
  },
  {
    username: "teacher",
    role: "TEACHER",
    run: "22222222-2",
    name: "Local Teacher",
    email: "teacher@example.test",
  },
  {
    username: "student",
    role: "STUDENT",
    run: "33333333-3",
    name: "Local Student",
    email: "student@example.test",
  },
];

export function requireLocalSetup(env, command) {
  if (env.LOCAL_DATA_SETUP_ENABLED !== "true" || env.NODE_ENV !== "development")
    throw new Error(
      "Explicit local development setup is required; this tool must not run in production",
    );
  if (!["all", "catalog", "accounts"].includes(command))
    throw new Error("Use all, catalog or accounts");
  if (command !== "catalog" && !env.LOCAL_TEST_PASSWORD?.trim())
    throw new Error("Set LOCAL_TEST_PASSWORD in the untracked root .env");
}

export function assertAcademicRole(account, roles, expectedRole) {
  if (
    account.serviceAccountClientId ||
    account.username.startsWith("service-account-")
  )
    throw new Error("Technical accounts cannot be changed by local data setup");
  if (
    roles.some(
      (role) =>
        ["ADMIN", "TEACHER", "STUDENT"].includes(role.name) &&
        role.name !== expectedRole,
    )
  )
    throw new Error(
      `Account ${account.username} has another academic role; manual review required`,
    );
}

export async function assertStudentPlanChange(client, student, targetPlanId) {
  if (String(student.study_plan_id) === String(targetPlanId)) return;
  const {
    rows: [activity],
  } = await client.query(
    `SELECT
    EXISTS(SELECT 1 FROM course_registrations WHERE student_id=$1) AS registrations,
    EXISTS(SELECT 1 FROM academic_records ar JOIN course_registrations cr ON cr.id=ar.registration_id WHERE cr.student_id=$1) AS history`,
    [student.id],
  );
  if (activity.registrations || activity.history)
    throw new Error(
      "The selected student already has academic activity; its plan cannot be replaced. Existing data was preserved",
    );
}

async function preflight(client, keycloak, definition, targetPlanId) {
  const account = await keycloak.findUser(definition.username);
  if (account) {
    const roles = await keycloak.request(
      "GET",
      `/users/${account.id}/role-mappings/realm/composite`,
    );
    assertAcademicRole(account, roles, definition.role);
  }
  const {
    rows: [local],
  } = account
    ? await client.query(
        "SELECT * FROM app_users WHERE keycloak_subject=$1 FOR UPDATE",
        [account.id],
      )
    : { rows: [] };
  if (local?.access_suspended)
    throw new Error(
      `Account ${definition.username} is locally suspended; use the normal recovery flow instead`,
    );
  // A missing local identity is never recovered by an email or RUN match.
  const { rows: collisions } = await client.query(
    "SELECT id FROM app_users WHERE (run=$1 OR lower(email)=lower($2)) AND id IS DISTINCT FROM $3::bigint",
    [
      local?.run || definition.run,
      local?.email || definition.email,
      local?.id || null,
    ],
  );
  if (collisions.length)
    throw new Error(
      `Reserved local identity for ${definition.username} is already used; no identities were relinked by email`,
    );
  if (
    account?.email &&
    local &&
    account.email.toLowerCase() !== local.email.toLowerCase()
  )
    throw new Error(
      `Account ${definition.username} has conflicting Keycloak/local email metadata; manual review required`,
    );
  const expectedEmail = local?.email || account?.email || definition.email;
  const emailUsers = await keycloak.request(
    "GET",
    `/users?email=${encodeURIComponent(expectedEmail)}&exact=true`,
  );
  if (emailUsers.some((user) => user.id !== account?.id))
    throw new Error(
      `Email for ${definition.username} belongs to a different Keycloak identity`,
    );
  let student;
  if (local) {
    const { rows: students } = await client.query(
      "SELECT * FROM students WHERE user_id=$1 FOR UPDATE",
      [local.id],
    );
    const { rows: teachers } = await client.query(
      "SELECT * FROM teachers WHERE user_id=$1 FOR UPDATE",
      [local.id],
    );
    if (
      (students.length && definition.role !== "STUDENT") ||
      (teachers.length && definition.role !== "TEACHER")
    )
      throw new Error(
        `Account ${definition.username} has another academic profile; manual review required`,
      );
    student = students[0];
    if (student) await assertStudentPlanChange(client, student, targetPlanId);
  }
  return { definition, account, local, student };
}

async function prepareProfile(client, person, targetPlanId) {
  const { definition, account } = person;
  if (!person.local) {
    const {
      rows: [local],
    } = await client.query(
      `INSERT INTO app_users(keycloak_subject,run,full_name,email,access_suspended,row_version)
      VALUES ($1,$2,$3,$4,false,0) RETURNING *`,
      [
        account.id,
        definition.run,
        definition.name,
        account.email || definition.email,
      ],
    );
    person.local = local;
  }
  if (definition.role === "STUDENT") {
    if (person.student) {
      if (String(person.student.study_plan_id) !== String(targetPlanId)) {
        await client.query(
          "UPDATE students SET study_plan_id=$1,row_version=row_version+1 WHERE id=$2",
          [targetPlanId, person.student.id],
        );
      }
    } else
      await client.query(
        "INSERT INTO students(user_id,study_plan_id,academic_status,row_version) VALUES ($1,$2,'REGULAR',0)",
        [person.local.id, targetPlanId],
      );
  }
  if (definition.role === "TEACHER") {
    await client.query(
      `INSERT INTO teachers(user_id,professional_title,academic_degree,status,row_version)
      VALUES ($1,'Ingeniero en Informática','Licenciado','ACTIVE',0) ON CONFLICT (user_id) DO NOTHING`,
      [person.local.id],
    );
  }
}

export async function prepareAccounts(
  client,
  keycloak,
  targetPlanId,
  password,
  log = console.log,
) {
  const created = [];
  let people;
  try {
    people = await inTransaction(client, async () => {
      await client.query(
        "SELECT pg_advisory_xact_lock(hashtext('siga-local-test-accounts'))",
      );
      const {
        rows: [plan],
      } = await client.query(
        `SELECT p.id FROM study_plans p JOIN degree_programs d ON d.id=p.degree_program_id
        WHERE p.id=$1 AND d.code='10450' AND p.plan_version='2021.3' AND p.status='CURRENT' AND d.status='ACTIVE' FOR UPDATE OF d,p`,
        [targetPlanId],
      );
      if (!plan)
        throw new Error(
          "Load the official Analista Programador plan before preparing local accounts",
        );
      const prepared = [];
      // Validate every existing identity before changing any account or password.
      for (const definition of accountDefinitions)
        prepared.push(
          await preflight(client, keycloak, definition, targetPlanId),
        );
      for (const person of prepared) {
        if (!person.account) {
          const split = person.definition.name.lastIndexOf(" ");
          const response = await keycloak.request("POST", "/users", {
            username: person.definition.username,
            email: person.definition.email,
            firstName: person.definition.name.slice(0, split),
            lastName: person.definition.name.slice(split + 1),
            enabled: false,
            emailVerified: false,
            requiredActions: [],
          });
          const location = response.headers.get("location");
          if (!location)
            throw new Error(
              "Keycloak created an account without returning its ID; manual reconciliation required",
            );
          const id = location.split("/").pop();
          person.account = {
            id,
            username: person.definition.username,
            email: person.definition.email,
          };
          created.push(id);
        }
        await prepareProfile(client, person, targetPlanId);
      }
      return prepared;
    });
  } catch (error) {
    for (const id of created) {
      try {
        await keycloak.request("DELETE", `/users/${id}`);
      } catch {
        log(
          `Could not remove incomplete test account ${id}; manual reconciliation required`,
        );
      }
    }
    throw error;
  }

  // Passwords cannot be rolled back: finalize only after all local profiles commit.
  // A provider failure here may leave a partial setup; re-running is supported.
  for (const person of people) {
    const { definition, account } = person;
    const role = await keycloak.request("GET", `/roles/${definition.role}`);
    await keycloak.request("POST", `/users/${account.id}/role-mappings/realm`, [
      role,
    ]);
    await keycloak.request("PUT", `/users/${account.id}/reset-password`, {
      type: "password",
      value: password,
      temporary: false,
    });
    await keycloak.request("PUT", `/users/${account.id}`, {
      enabled: true,
    });
    await keycloak.request("POST", `/users/${account.id}/logout`);
    log(
      `Ready: ${definition.username} (${definition.role}); existing subject and academic references preserved`,
    );
  }
  return people;
}
