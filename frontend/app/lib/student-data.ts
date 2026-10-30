import { get } from "./api";
import type { Plan, Program, Student } from "./types";
export async function loadStudent(signal: AbortSignal) {
  const student = await get<Student>("/students/me", signal);
  const [program, plan] = await Promise.all([
    get<Program>(`/degree-programs/${student.degreeProgramId}`, signal),
    get<Plan>(`/study-plans/${student.studyPlanId}`, signal),
  ]);
  return { student, program, plan };
}
