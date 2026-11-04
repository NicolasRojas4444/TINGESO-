import type { Role } from "../lib/types";

export const navigation: Record<Role, [string, string][]> = {
  ADMIN: [
    ["/admin/students", "Estudiantes"],
    ["/admin/teachers", "Docentes"],
    ["/admin/programs", "Carreras y planes"],
    ["/admin/periods", "Períodos y oferta"],
  ],
  TEACHER: [["/teacher/sections", "Mis secciones"]],
  STUDENT: [
    ["/student/profile", "Mi información"],
    ["/student/plan", "Mi plan de estudios"],
    ["/student/schedule", "Mis ramos y horario"],
    ["/student/registration", "Inscripción académica"],
    ["/student/history", "Historial académico"],
  ],
};
