import type { Role } from "../lib/types";

export const roleLabels: Record<Role, string> = {
  ADMIN: "Administración",
  TEACHER: "Docencia",
  STUDENT: "Estudiante",
};
export function homeForRole(role: Role) {
  return role === "ADMIN"
    ? "/admin/students"
    : role === "TEACHER"
      ? "/teacher/sections"
      : "/student/profile";
}
