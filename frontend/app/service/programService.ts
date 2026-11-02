import { api, get } from "./http";
import type { Program } from "../lib/types";

export type ProgramInput = Pick<Program, "code" | "name" | "description">;
const url = "/degree-programs";

export const programService = {
  getAll: (signal?: AbortSignal) => get<Program[]>(url, signal),
  getById: (id: number, signal?: AbortSignal) =>
    get<Program>(`${url}/${id}`, signal),
  create: async (input: ProgramInput) =>
    (await api.post<Program>(url, input)).data,
  update: async (id: number, input: ProgramInput) =>
    (await api.put<Program>(`${url}/${id}`, input)).data,
  changeStatus: async (id: number, status: string) =>
    (await api.put<Program>(`${url}/${id}/status`, { status })).data,
  remove: async (id: number) => {
    await api.delete(`${url}/${id}`);
  },
};
