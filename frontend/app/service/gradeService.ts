import { api, get } from "./http";
import type { Grade } from "../lib/types";

const url = "/final-grades";
export const gradeService = {
  getBySection: (id: number, signal?: AbortSignal) =>
    get<Grade[]>(`${url}/by-section/${id}`, signal),
  create: async (registrationId: number, value: number) =>
    (
      await api.post<Grade>(`${url}/by-registration/${registrationId}`, {
        value,
      })
    ).data,
  update: async (registrationId: number, value: number) =>
    (
      await api.put<Grade>(`${url}/by-registration/${registrationId}`, {
        value,
      })
    ).data,
};
