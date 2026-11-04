import assert from "node:assert/strict";
import pg from "pg";

const [year, semester] = process.argv.slice(2).map(Number);
if (!Number.isInteger(year) || ![1, 2].includes(semester)) throw new Error("Specify offering YEAR SEMESTER");
const db = new pg.Client();
try {
  await db.connect();
  await db.query("BEGIN READ ONLY");
  const period = (await db.query("SELECT id,status FROM academic_periods WHERE academic_year=$1 AND semester=$2", [year, semester])).rows[0];
  assert.equal(period?.status, "OPEN");
  const counts = (await db.query(`SELECT d.code,count(*)::int AS sections FROM sections s
    JOIN courses c ON c.id=s.course_id JOIN study_plans p ON p.id=c.study_plan_id JOIN degree_programs d ON d.id=p.degree_program_id
    WHERE s.academic_period_id=$1 GROUP BY d.code ORDER BY d.code`, [period.id])).rows;
  assert.deepEqual(counts.filter(p => ["10450", "10451", "10452"].includes(p.code)),
    ["10450", "10451", "10452"].map(code => ({ code, sections: 24 })));
  assert.equal((await db.query(`SELECT s.id FROM sections s JOIN courses c ON c.id=s.course_id
    LEFT JOIN section_schedule_blocks b ON b.section_id=s.id WHERE s.academic_period_id=$1
    GROUP BY s.id,c.id HAVING count(b.id)*2<>c.theory_hours+c.exercise_hours+c.laboratory_hours`, [period.id])).rowCount, 0, "TEL mismatch");
  assert.equal((await db.query(`SELECT 1 FROM sections s JOIN section_schedule_blocks b ON b.section_id=s.id
    WHERE s.academic_period_id=$1 GROUP BY s.teacher_id,b.day_of_week,b.module HAVING count(*)>1`, [period.id])).rowCount, 0, "Teacher conflict");
  assert.equal((await db.query(`SELECT 1 FROM sections s JOIN courses c ON c.id=s.course_id JOIN section_schedule_blocks b ON b.section_id=s.id
    WHERE s.academic_period_id=$1 GROUP BY c.study_plan_id,c.semester,b.day_of_week,b.module HAVING count(*)>1`, [period.id])).rowCount, 0, "Curricular conflict");
  assert.equal((await db.query(`SELECT s.id FROM students s JOIN app_users u ON u.id=s.user_id
    WHERE s.academic_status='REGULAR' AND NOT u.access_suspended
    AND EXISTS(SELECT 1 FROM sections x JOIN courses c ON c.id=x.course_id WHERE x.academic_period_id=$1 AND c.study_plan_id=s.study_plan_id)
    AND NOT EXISTS(SELECT 1 FROM enrollments e WHERE e.student_id=s.id AND e.academic_period_id=$1)`, [period.id])).rowCount, 0, "Missing current enrollment");
  await db.query("COMMIT");
  console.log(`PASS: ${year}-${semester}: 24 sections per official plan, exact TEL, no teacher/curricular conflicts, all eligible current students enrolled. Read-only verification.`);
} finally { await db.end(); }
