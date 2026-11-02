import { readFile } from "node:fs/promises";

export async function loadCatalog() {
  return validateCatalog(
    JSON.parse(
      await readFile(new URL("./catalog.json", import.meta.url), "utf8"),
    ),
  );
}

export function validateCatalog(input) {
  if (!Array.isArray(input.programs) || input.programs.length !== 3)
    throw new Error("Expected the three Appendix A programs");
  const expected = ["10450:2021.3", "10451:2022.3", "10452:2021.3"];
  const programs = input.programs.map((program, index) => {
    if (
      `${program.code}:${program.version}` !== expected[index] ||
      !program.name ||
      program.courses?.length !== 24
    )
      throw new Error("Invalid Appendix A program or course count");
    const courses = program.courses.map((row) => {
      if (!Array.isArray(row) || row.length !== 8)
        throw new Error("Invalid course row");
      const [
        code,
        name,
        semester,
        theory,
        exercise,
        laboratory,
        sct,
        prerequisites,
      ] = row;
      if (
        typeof code !== "string" ||
        !name ||
        ![1, 2, 3, 4].includes(semester) ||
        ![theory, exercise, laboratory].every(
          (hours) => Number.isInteger(hours) && hours >= 0,
        ) ||
        theory + exercise + laboratory >= 8 ||
        !Number.isInteger(sct) ||
        sct < 1 ||
        sct > 7 ||
        !Array.isArray(prerequisites) ||
        prerequisites.length > 3 ||
        new Set(prerequisites).size !== prerequisites.length
      )
        throw new Error(`Invalid course: ${code}`);
      return {
        code,
        name,
        semester,
        theory,
        exercise,
        laboratory,
        sct,
        prerequisites,
      };
    });
    const byCode = new Map(courses.map((course) => [course.code, course]));
    if (
      byCode.size !== 24 ||
      [1, 2, 3, 4].some(
        (semester) =>
          courses.filter((course) => course.semester === semester).length !== 6,
      )
    )
      throw new Error("Expected six unique courses per semester");
    for (const course of courses) {
      if (
        course.prerequisites.some(
          (code) =>
            !byCode.has(code) || byCode.get(code).semester >= course.semester,
        )
      )
        throw new Error(`Invalid prerequisite: ${course.code}`);
    }
    return { ...program, courses };
  });
  return { source: input.source, programs };
}

export async function inTransaction(client, operation) {
  await client.query("BEGIN");
  try {
    const result = await operation();
    await client.query("COMMIT");
    return result;
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  }
}

export async function importCatalog(client, catalog) {
  return inTransaction(client, async () => {
    await client.query(
      "SELECT pg_advisory_xact_lock(hashtext('siga-appendix-a-import'))",
    );
    const plans = new Map();
    for (const program of catalog.programs) {
      await client.query(
        `INSERT INTO degree_programs(code,name,description,duration_semesters,status,row_version)
        VALUES ($1,$2,$3,4,'ACTIVE',0) ON CONFLICT (code) DO NOTHING`,
        [
          program.code,
          program.name,
          "Carrera técnica de dos años y cuatro semestres académicos.",
        ],
      );
      const {
        rows: [existing],
      } = await client.query(
        "SELECT * FROM degree_programs WHERE code=$1 FOR UPDATE",
        [program.code],
      );
      if (
        existing.name !== program.name ||
        existing.duration_semesters !== 4 ||
        existing.status !== "ACTIVE"
      )
        throw new Error(
          `Program ${program.code} differs from Appendix A; existing data was not overwritten`,
        );
      const { rows: current } = await client.query(
        "SELECT id,plan_version FROM study_plans WHERE degree_program_id=$1 AND status='CURRENT'",
        [existing.id],
      );
      if (
        current.some((plan) => plan.plan_version !== program.version) ||
        current.length > 1
      )
        throw new Error(
          `Program ${program.code} already has a different current plan; manual review required`,
        );
      await client.query(
        `INSERT INTO study_plans(degree_program_id,plan_version,status,row_version)
        VALUES ($1,$2,'CURRENT',0) ON CONFLICT (degree_program_id,plan_version) DO NOTHING`,
        [existing.id, program.version],
      );
      const {
        rows: [plan],
      } = await client.query(
        "SELECT * FROM study_plans WHERE degree_program_id=$1 AND plan_version=$2 FOR UPDATE",
        [existing.id, program.version],
      );
      if (plan.status !== "CURRENT")
        throw new Error(
          `Plan ${program.code}/${program.version} is not current; existing state preserved`,
        );
      plans.set(program.code, plan.id);
      const ids = new Map();
      for (const course of program.courses) {
        await client.query(
          `INSERT INTO courses(study_plan_id,code,name,semester,theory_hours,exercise_hours,laboratory_hours,sct,row_version)
          VALUES ($1,$2,$3,$4,$5,$6,$7,$8,0) ON CONFLICT (study_plan_id,code) DO NOTHING`,
          [
            plan.id,
            course.code,
            course.name,
            course.semester,
            course.theory,
            course.exercise,
            course.laboratory,
            course.sct,
          ],
        );
        const {
          rows: [row],
        } = await client.query(
          "SELECT * FROM courses WHERE study_plan_id=$1 AND code=$2",
          [plan.id, course.code],
        );
        if (
          row.name !== course.name ||
          row.semester !== course.semester ||
          row.theory_hours !== course.theory ||
          row.exercise_hours !== course.exercise ||
          row.laboratory_hours !== course.laboratory ||
          row.sct !== course.sct
        )
          throw new Error(
            `Course ${course.code} differs from Appendix A; existing data was not overwritten`,
          );
        ids.set(course.code, row.id);
      }
      const { rows: allCourses } = await client.query(
        "SELECT code FROM courses WHERE study_plan_id=$1",
        [plan.id],
      );
      if (allCourses.length !== 24)
        throw new Error(
          `Plan ${program.code} contains additional courses; manual review required`,
        );
      for (const course of program.courses) {
        const { rows: links } = await client.query(
          `SELECT p.id,p.code FROM course_prerequisites cp JOIN courses p ON p.id=cp.prerequisite_id WHERE cp.course_id=$1`,
          [ids.get(course.code)],
        );
        if (
          links.some(
            (link) =>
              !course.prerequisites.includes(link.code) ||
              String(ids.get(link.code)) !== String(link.id),
          )
        )
          throw new Error(
            `Course ${course.code} has conflicting prerequisites; existing links preserved`,
          );
        for (const prerequisite of course.prerequisites) {
          await client.query(
            `INSERT INTO course_prerequisites(course_id,prerequisite_id,row_version) VALUES ($1,$2,0)
            ON CONFLICT (course_id,prerequisite_id) DO NOTHING`,
            [ids.get(course.code), ids.get(prerequisite)],
          );
        }
      }
    }
    return plans;
  });
}
