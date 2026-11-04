import { api, get } from "./http";
import type { Teacher } from "../lib/types";
import type { UserInput } from "./studentService";

export interface TeacherInput {
  user: UserInput;
  professionalTitle: string;
  academicDegree: string;
}
const url = "/teachers";
export const teacherService = {
  getAll: (signal?: AbortSignal) => get<Teacher[]>(url, signal),
  getMe: (signal?: AbortSignal) => get<Teacher>(`${url}/me`, signal),
  getById: (id: number, signal?: AbortSignal) =>
    get<Teacher>(`${url}/${id}`, signal),
  update: async (id: number, input: TeacherInput) =>
    (await api.put<Teacher>(`${url}/${id}`, input)).data,
  changeStatus: async (id: number, status: string) =>
    (await api.put<Teacher>(`${url}/${id}/status`, { status })).data,
  remove: async (id: number) => {
    await api.delete(`${url}/${id}`);
  },
};
