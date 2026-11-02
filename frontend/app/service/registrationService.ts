import { api, get } from "./http";
import type { Registration, Section } from "../lib/types";

const url = "/registrations";
const selectionUrl = (studentId: number, periodId: number) =>
  `${url}?studentId=${studentId}&periodId=${periodId}`;
export const registrationService = {
  getByStudent: (id: number, signal?: AbortSignal) =>
    get<Registration[]>(`${url}/by-student/${id}`, signal),
  getBySection: (id: number, signal?: AbortSignal) =>
    get<Registration[]>(`${url}/by-section/${id}`, signal),
  getReplacementOptions: (
    studentId: number,
    periodId: number,
    signal?: AbortSignal,
  ) =>
    get<Section[]>(
      `${url}/replacement-options?studentId=${studentId}&periodId=${periodId}`,
      signal,
    ),
  register: async (studentId: number, periodId: number, sectionIds: number[]) =>
    (
      await api.post<Registration[]>(
        selectionUrl(studentId, periodId),
        sectionIds.map((sectionId) => ({ sectionId })),
      )
    ).data,
  replace: async (studentId: number, periodId: number, sectionIds: number[]) =>
    (
      await api.put<Registration[]>(selectionUrl(studentId, periodId), {
        sectionIds,
      })
    ).data,
  cancel: async (id: number) =>
    (await api.delete<Registration>(`${url}/${id}`)).data,
};
