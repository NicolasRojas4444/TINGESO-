import { api, get } from "./http";
import type { Section } from "../lib/types";

export type SectionInput = Pick<
  Section,
  "courseId" | "academicPeriodId" | "teacherId" | "capacity" | "scheduleBlocks"
>;
const url = "/sections";
export const sectionService = {
  getById: (id: number, signal?: AbortSignal) =>
    get<Section>(`${url}/${id}`, signal),
  getByPeriod: (id: number, signal?: AbortSignal) =>
    get<Section[]>(`${url}/by-period/${id}`, signal),
  getByTeacher: (id: number, signal?: AbortSignal) =>
    get<Section[]>(`${url}/by-teacher/${id}`, signal),
  create: async (input: SectionInput) =>
    (await api.post<Section>(url, input)).data,
  update: async (id: number, input: SectionInput) =>
    (await api.put<Section>(`${url}/${id}`, input)).data,
  remove: async (id: number) => {
    await api.delete(`${url}/${id}`);
  },
};
