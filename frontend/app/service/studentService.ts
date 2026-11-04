import { api, get } from "./http";
import type { AcademicRecord, Student, User } from "../lib/types";
import { programService } from "./programService";
import { studyPlanService } from "./studyPlanService";

export type UserInput = Pick<User, "run" | "fullName" | "email">;
export interface StudentInput {
  user: UserInput;
  degreeProgramId?: number;
}
const url = "/students";
export const studentService = {
  getAll: (signal?: AbortSignal) => get<Student[]>(url, signal),
  getMe: (signal?: AbortSignal) => get<Student>(`${url}/me`, signal),
  getById: (id: number, signal?: AbortSignal) =>
    get<Student>(`${url}/${id}`, signal),
  getHistory: (id: number, signal?: AbortSignal) =>
    get<AcademicRecord[]>(`${url}/${id}/history`, signal),
  update: async (id: number, input: Pick<StudentInput, "user">) =>
    (await api.put<Student>(`${url}/${id}`, input)).data,
  changeStatus: async (id: number, academicStatus: string) =>
    (await api.put<Student>(`${url}/${id}/status`, { academicStatus })).data,
  changePlan: async (id: number, studyPlanId: number) =>
    (await api.put<Student>(`${url}/${id}/study-plan`, { studyPlanId })).data,
  remove: async (id: number) => {
    await api.delete(`${url}/${id}`);
  },
  async getOverview(signal?: AbortSignal) {
    const student = await studentService.getMe(signal);
    const [program, plan] = await Promise.all([
      programService.getById(student.degreeProgramId, signal),
      studyPlanService.getById(student.studyPlanId, signal),
    ]);
    return { student, program, plan };
  },
};
