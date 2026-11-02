import { api, get } from "./http";
import type { Plan } from "../lib/types";

const url = "/study-plans";
export const studyPlanService = {
  getAll: (signal?: AbortSignal) => get<Plan[]>(url, signal),
  getById: (id: number, signal?: AbortSignal) =>
    get<Plan>(`${url}/${id}`, signal),
  getByProgram: (id: number, signal?: AbortSignal) =>
    get<Plan[]>(`${url}/by-degree-program/${id}`, signal),
  create: async (input: Omit<Plan, "id">) =>
    (await api.post<Plan>(url, input)).data,
  update: async (id: number, version: string) =>
    (await api.put<Plan>(`${url}/${id}`, { version })).data,
  setCurrent: async (id: number) =>
    (await api.put<Plan>(`${url}/${id}/current`)).data,
  remove: async (id: number) => {
    await api.delete(`${url}/${id}`);
  },
};
