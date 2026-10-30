import { get } from "./api";
import type { Course } from "./types";

export async function loadCurriculum(
  planId: number | string,
  signal: AbortSignal,
) {
  const courses = await get<Course[]>(
    `/courses/by-study-plan/${planId}`,
    signal,
  );
  const entries = await Promise.all(
    courses.map(
      async (course) =>
        [
          course.id,
          await get<Course[]>(`/courses/${course.id}/prerequisites`, signal),
        ] as const,
    ),
  );
  return {
    courses,
    prerequisites: Object.fromEntries(entries) as Record<number, Course[]>,
  };
}
