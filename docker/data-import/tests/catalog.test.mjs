import { test } from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { loadCatalog, validateCatalog, inTransaction } from "../catalog.mjs";
import {
  requireLocalSetup,
  assertAcademicRole,
  assertStudentPlanChange,
  accountDefinitions,
} from "../accounts.mjs";
import { KeycloakAdmin } from "../keycloak.mjs";

const raw = JSON.parse(
  await readFile(new URL("../catalog.json", import.meta.url), "utf8"),
);
test("Appendix A contains 72 courses, four semesters and all three official plan versions", async () => {
  const catalog = await loadCatalog();
  assert.deepEqual(
    catalog.programs.map((program) => [program.code, program.version]),
    [
      ["10450", "2021.3"],
      ["10451", "2022.3"],
      ["10452", "2021.3"],
    ],
  );
  assert.equal(
    catalog.programs.flatMap((program) => program.courses).length,
    72,
  );
  for (const program of catalog.programs) {
    for (const semester of [1, 2, 3, 4])
      assert.equal(
        program.courses.filter((course) => course.semester === semester).length,
        6,
      );
  }
});
test("preserves multi-prerequisite capstones and independent final-semester courses from the PDF", async () => {
  const catalog = await loadCatalog();
  const byCode = new Map(
    catalog.programs
      .flatMap((program) => program.courses)
      .map((course) => [course.code, course]),
  );
  assert.deepEqual(byCode.get("TAP405").prerequisites, [
    "TAP301",
    "TAP302",
    "TAP306",
  ]);
  assert.deepEqual(byCode.get("TAD405").prerequisites, [
    "TAD302",
    "TAD303",
    "TAD305",
  ]);
  assert.deepEqual(byCode.get("TCS405").prerequisites, [
    "TCS301",
    "TCS302",
    "TCS305",
  ]);
  assert.deepEqual(byCode.get("TAD406").prerequisites, []);
  assert.equal(byCode.get("TAP204").laboratory, 4);
  assert.equal(byCode.get("TAD105").theory, 0);
});
for (const [name, change] of [
  ["missing courses", (copy) => copy.programs[0].courses.pop()],
  [
    "duplicate codes",
    (copy) => {
      copy.programs[0].courses[1][0] = "TAP101";
    },
  ],
  [
    "negative hours",
    (copy) => {
      copy.programs[0].courses[0][3] = -1;
    },
  ],
  [
    "invalid SCT",
    (copy) => {
      copy.programs[0].courses[0][6] = 8;
    },
  ],
  [
    "foreign prerequisite",
    (copy) => {
      copy.programs[0].courses[6][7] = ["TAD101"];
    },
  ],
  [
    "same-semester prerequisite",
    (copy) => {
      copy.programs[0].courses[0][7] = ["TAP102"];
    },
  ],
  [
    "too many prerequisites",
    (copy) => {
      copy.programs[0].courses[23][7] = [
        "TAP101",
        "TAP102",
        "TAP103",
        "TAP104",
      ];
    },
  ],
]) {
  test(`rejects ${name} before any database changes`, () => {
    const copy = structuredClone(raw);
    change(copy);
    assert.throws(() => validateCatalog(copy));
  });
}
test("requires explicit development opt-in and a password only for account setup", () => {
  assert.throws(() => requireLocalSetup({}, "all"));
  assert.throws(() =>
    requireLocalSetup(
      {
        LOCAL_DATA_SETUP_ENABLED: "true",
        NODE_ENV: "production",
        LOCAL_TEST_PASSWORD: "test-only",
      },
      "all",
    ),
  );
  const env = { LOCAL_DATA_SETUP_ENABLED: "true", NODE_ENV: "development" };
  assert.doesNotThrow(() => requireLocalSetup(env, "catalog"));
  assert.throws(() => requireLocalSetup(env, "accounts"));
  assert.doesNotThrow(() =>
    requireLocalSetup({ ...env, LOCAL_TEST_PASSWORD: "test-only" }, "all"),
  );
  assert.throws(() => requireLocalSetup(env, "unknown"));
});
test("never modifies service accounts or mixes academic roles", () => {
  assert.throws(() =>
    assertAcademicRole({ username: "service-account-any" }, [], "ADMIN"),
  );
  assert.throws(() =>
    assertAcademicRole(
      { username: "human", serviceAccountClientId: "client" },
      [],
      "ADMIN",
    ),
  );
  assert.throws(() =>
    assertAcademicRole({ username: "teacher" }, [{ name: "ADMIN" }], "TEACHER"),
  );
  assert.doesNotThrow(() =>
    assertAcademicRole(
      { username: "student" },
      [{ name: "default-roles-siga" }, { name: "STUDENT" }],
      "STUDENT",
    ),
  );
  assert.deepEqual(
    accountDefinitions.map((person) => person.username),
    ["admin", "teacher", "student"],
  );
  assert.ok(accountDefinitions.every((person) => !("password" in person)));
  assert.throws(
    () =>
      new KeycloakAdmin({
        KEYCLOAK_SERVER_URL: "http://iam",
        KEYCLOAK_REALM: "master",
        KEYCLOAK_ADMIN: "admin",
        KEYCLOAK_ADMIN_PASSWORD: "test-only",
      }),
  );
});
test("plan migration never discards registrations or history", async () => {
  for (const activity of [
    { registrations: true, history: false },
    { registrations: false, history: true },
  ]) {
    await assert.rejects(
      () =>
        assertStudentPlanChange(
          { query: async () => ({ rows: [activity] }) },
          { id: 1, study_plan_id: 1 },
          2,
        ),
      /academic activity/,
    );
  }
  await assert.doesNotReject(() =>
    assertStudentPlanChange(
      {
        query: async () => ({
          rows: [{ registrations: false, history: false }],
        }),
      },
      { id: 1, study_plan_id: 1 },
      2,
    ),
  );
  await assert.doesNotReject(() =>
    assertStudentPlanChange(
      {
        query: () => {
          throw new Error("No query expected");
        },
      },
      { id: 1, study_plan_id: 2 },
      2,
    ),
  );
});
test("commits successful imports and rolls back failed imports", async () => {
  const calls = [];
  const client = { query: async (sql) => calls.push(sql) };
  assert.equal(await inTransaction(client, async () => "ready"), "ready");
  assert.deepEqual(calls, ["BEGIN", "COMMIT"]);
  calls.length = 0;
  await assert.rejects(
    () =>
      inTransaction(client, async () => {
        throw new Error("conflict");
      }),
    /conflict/,
  );
  assert.deepEqual(calls, ["BEGIN", "ROLLBACK"]);
});
