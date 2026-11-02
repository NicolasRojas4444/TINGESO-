import { api, get } from "./http";
import type { Period } from "../lib/types";

const url = "/academic-periods";
export const academicPeriodService = {
  getAll: (signal?: AbortSignal) => get<Period[]>(url, signal),
  getById: (id: number, signal?: AbortSignal) =>
    get<Period>(`${url}/${id}`, signal),
  create: async (input: Pick<Period, "year" | "semester">) =>
    (await api.post<Period>(url, input)).data,
  close: async (id: number) =>
    (await api.post<Period>(`${url}/${id}/close`)).data,
};
