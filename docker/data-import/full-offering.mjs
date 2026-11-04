import { inTransaction } from "./catalog.mjs";

const seedKey = "official-offering-v2";
const days = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];

// Pure scheduler: preserve existing blocks, separate each curricular semester,
// and reuse available teachers before requesting another one.
export function chooseSlots(groupBusy, teacherBusy, count) {
  const free = Array.from({ length: 30 }, (_, i) => i)
    .filter(slot => !groupBusy.has(slot) && !teacherBusy.has(slot));
  return free.length >= count ? free.slice(0, count) : null;
}

export async function enrollCurrentStudents(db, periodId) {
  return (await db.query(`INSERT INTO enrollments(row_version,student_id,academic_period_id,enrolled_at)
    SELECT 0,s.id,p.id,now() FROM students s JOIN app_users u ON u.id=s.user_id
    CROSS JOIN academic_periods p
    WHERE p.id=$1 AND p.status='OPEN' AND s.academic_status='REGULAR' AND NOT u.access_suspended
      AND EXISTS(SELECT 1 FROM sections x JOIN courses c ON c.id=x.course_id
        WHERE x.academic_period_id=p.id AND c.study_plan_id=s.study_plan_id)
      AND NOT EXISTS(SELECT 1 FROM enrollments e WHERE e.student_id=s.id AND e.academic_period_id=p.id)
    RETURNING id`, [periodId])).rowCount;
}

export async function initializeFullOffering(db, kc, password) {
  const created = [];
  await db.query("SELECT pg_advisory_lock(hashtext('siga-official-demo-bootstrap'))");
  try {
    if ((await db.query("SELECT 1 FROM app_seed_state WHERE seed_key=$1", [seedKey])).rowCount)
      return { existing: true };
    return await inTransaction(db, async () => {
      const insert = async (sql, values = []) => (await db.query(sql + " RETURNING id", values)).rows[0].id;
      let period = (await db.query("SELECT * FROM academic_periods WHERE academic_year=2099 AND semester=2 FOR UPDATE")).rows[0];
      if (!period) throw new Error("Initialize the official catalog before expanding its offering");
      if (period.status !== "OPEN") {
        // Never reopen or reuse an unrelated period.
        const year = (await db.query("SELECT greatest(2100,coalesce(max(academic_year),2099)+1)::int AS year FROM academic_periods")).rows[0].year;
        period = { id: await insert("INSERT INTO academic_periods(row_version,academic_year,semester,status) VALUES (0,$1,1,'OPEN')", [year]), academic_year: year, semester: 1 };
      }
      const courses = (await db.query(`SELECT c.* FROM courses c JOIN study_plans p ON p.id=c.study_plan_id
        JOIN degree_programs d ON d.id=p.degree_program_id
        WHERE (d.code='10450' AND p.plan_version='2021.3')
           OR (d.code='10451' AND p.plan_version='2022.3')
           OR (d.code='10452' AND p.plan_version='2021.3')
        ORDER BY c.study_plan_id,c.semester,c.code`)).rows;
      if (courses.length !== 72) throw new Error("Expected the 72 official courses; no offering was changed");
      const teachers = (await db.query(`SELECT t.id FROM teachers t JOIN app_users u ON u.id=t.user_id
        WHERE t.status='ACTIVE' AND NOT u.access_suspended ORDER BY t.id`)).rows;
      const teacherSlots = new Map(teachers.map(t => [String(t.id), new Set()]));
      const groups = new Map();
      const key = c => `${c.study_plan_id}:${c.semester}`;
      const existing = (await db.query(`SELECT s.course_id,s.teacher_id,c.study_plan_id,c.semester,b.day_of_week,b.module
        FROM sections s JOIN courses c ON c.id=s.course_id
        LEFT JOIN section_schedule_blocks b ON b.section_id=s.id WHERE s.academic_period_id=$1`, [period.id])).rows;
      const offered = new Set(existing.map(s => String(s.course_id)));
      for (const block of existing) {
        if (!block.day_of_week) continue;
        const slot = days.indexOf(block.day_of_week) * 6 + block.module - 1;
        if (slot < 0 || slot >= 30) throw new Error("Existing schedule has an unsupported block; preserved for manual review");
        if (!groups.has(key(block))) groups.set(key(block), new Set());
        groups.get(key(block)).add(slot);
        teacherSlots.get(String(block.teacher_id))?.add(slot);
      }
      let addedTeachers = 0;
      const names = ["Camila Torres", "Diego Morales", "Valentina Soto", "Felipe Herrera", "Catalina Rojas", "Andrés Silva", "Daniela Muñoz", "Javier Castro"];
      for (const course of courses) {
        if (offered.has(String(course.id))) continue;
        const count = (course.theory_hours + course.exercise_hours + course.laboratory_hours) / 2;
        if (!Number.isInteger(count) || count < 1) throw new Error("Invalid official TEL hours");
        if (!groups.has(key(course))) groups.set(key(course), new Set());
        const group = groups.get(key(course));
        if (!chooseSlots(group, new Set(), count)) throw new Error("Existing curricular schedule cannot accommodate the missing course");
        let teacher = teachers.find(t => chooseSlots(group, teacherSlots.get(String(t.id)), count));
        if (!teacher) {
          const index = addedTeachers++;
          if (index >= names.length) throw new Error("Offering requires more teachers than expected");
          const username = `profesor.oferta.${index + 1}`;
          const email = `${username}@siga.example.test`;
          const number = 91000010 + index;
          let sum = 0, factor = 2;
          for (const digit of String(number).split("").reverse()) { sum += Number(digit) * factor; factor = factor === 7 ? 2 : factor + 1; }
          const check = 11 - sum % 11;
          const run = `${number}-${check === 11 ? "0" : check === 10 ? "K" : check}`;
          if (await kc.findUser(username) || (await kc.request("GET", `/users?email=${encodeURIComponent(email)}&exact=true`)).length
            || (await db.query("SELECT 1 FROM app_users WHERE run=$1 OR lower(email)=lower($2)", [run, email])).rowCount)
            throw new Error("An offering teacher identity is already in use; no account was overwritten");
          const role = await kc.request("GET", "/roles/TEACHER");
          const response = await kc.request("POST", "/users", { username, email, firstName: names[index].split(" ")[0], lastName: names[index].split(" ").slice(1).join(" "), enabled: false, requiredActions: [], credentials: [{ type: "password", value: password, temporary: false }] });
          const subject = response.headers.get("location")?.split("/").pop();
          if (!subject) throw new Error("Missing teacher account ID; manual reconciliation required");
          created.push(subject);
          await kc.request("POST", `/users/${subject}/role-mappings/realm`, [role]);
          const user = await insert("INSERT INTO app_users(row_version,keycloak_subject,run,full_name,email,access_suspended) VALUES (0,$1,$2,$3,$4,false)", [subject, run, names[index], email]);
          teacher = { id: await insert("INSERT INTO teachers(row_version,user_id,professional_title,academic_degree,status) VALUES (0,$1,'Ingeniero en Informática','Licenciado','ACTIVE')", [user]) };
          teachers.push(teacher);
          teacherSlots.set(String(teacher.id), new Set());
        }
        const busy = teacherSlots.get(String(teacher.id));
        const slots = chooseSlots(group, busy, count);
        const section = await insert("INSERT INTO sections(row_version,course_id,academic_period_id,teacher_id,capacity) VALUES (0,$1,$2,$3,30)", [course.id, period.id, teacher.id]);
        for (const slot of slots) {
          await db.query("INSERT INTO section_schedule_blocks(row_version,section_id,day_of_week,module) VALUES (0,$1,$2,$3)", [section, days[Math.floor(slot / 6)], slot % 6 + 1]);
          group.add(slot); busy.add(slot);
        }
      }
      const enrolled = await enrollCurrentStudents(db, period.id);
      for (const subject of created) await kc.request("PUT", `/users/${subject}`, { enabled: true });
      await db.query("INSERT INTO app_seed_state(seed_key,completed_at) VALUES ($1,now())", [seedKey]);
      return { existing: false, period: `${period.academic_year}-${period.semester}`, addedTeachers, enrolled };
    });
  } catch (error) {
    for (const subject of created) {
      try { await kc.request("DELETE", `/users/${subject}`); }
      catch { console.error(`Offering teacher ${subject} requires manual cleanup`); }
    }
    throw error;
  } finally { await db.query("SELECT pg_advisory_unlock(hashtext('siga-official-demo-bootstrap'))"); }
}
