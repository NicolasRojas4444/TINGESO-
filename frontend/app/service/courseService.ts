import { api, get } from "./http";
import type { Course } from "../lib/types";

const url = "/courses";
export type CourseInput = Omit<Course, "id">;
export const courseService = {
  getAll: (signal?: AbortSignal) => get<Course[]>(url, signal),
  getByPlan: (id: number, signal?: AbortSignal) =>
    get<Course[]>(`${url}/by-study-plan/${id}`, signal),
  getPrerequisites: (id: number, signal?: AbortSignal) =>
    get<Course[]>(`${url}/${id}/prerequisites`, signal),
  create: async (input: CourseInput) =>
    (await api.post<Course>(url, input)).data,
  update: async (id: number, input: CourseInput) =>
    (await api.put<Course>(`${url}/${id}`, input)).data,
  setPrerequisites: async (id: number, ids: number[]) =>
    (await api.put<Course[]>(`${url}/${id}/prerequisites`, ids)).data,
  remove: async (id: number) => {
    await api.delete(`${url}/${id}`);
  },
  async getCurriculum(planId: number, signal?: AbortSignal) {
    const courses = await courseService.getByPlan(planId, signal);
    const entries = await Promise.all(
      courses.map(
        async (course) =>
          [
            course.id,
            await courseService.getPrerequisites(course.id, signal),
          ] as const,
      ),
    );
    return {
      courses,
      prerequisites: Object.fromEntries(entries) as Record<number, Course[]>,
    };
  },
};
