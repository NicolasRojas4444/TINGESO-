export type Role = "ADMIN" | "TEACHER" | "STUDENT";
export interface User {
  id: number;
  keycloakSubject: string;
  run: string;
  fullName: string;
  email: string;
}
export interface Student {
  id: number;
  user: User;
  studyPlanId: number;
  degreeProgramId: number;
  academicStatus: string;
}
export interface Teacher {
  id: number;
  user: User;
  professionalTitle: string;
  academicDegree: string;
  status: string;
}
export interface Program {
  id: number;
  code: string;
  name: string;
  description: string;
  durationSemesters: number;
  status: string;
}
export interface Plan {
  id: number;
  degreeProgramId: number;
  version: string;
  status: string;
}
export interface Course {
  id: number;
  studyPlanId: number;
  code: string;
  name: string;
  semester: number;
  theoryHours: number;
  exerciseHours: number;
  laboratoryHours: number;
  sct: number;
}
export interface Period {
  id: number;
  year: number;
  semester: number;
  status: string;
  closedAt: string | null;
}
export interface ScheduleBlock {
  dayOfWeek: string;
  module: number;
}
export interface Section {
  id: number;
  courseId: number;
  academicPeriodId: number;
  teacherId: number;
  courseName: string;
  teacherName: string;
  capacity: number;
  registeredCount: number;
  availableSeats: number;
  scheduleBlocks: ScheduleBlock[];
}
export interface Registration {
  id: number;
  studentId: number;
  sectionId: number;
  courseId: number;
  academicPeriodId: number;
  studentName: string;
  courseName: string;
  status: string;
  registeredAt: string;
}
export interface Grade {
  id: number;
  registrationId: number;
  studentId: number;
  sectionId: number;
  value: number;
  result: string;
}
export interface AcademicRecord {
  id: number;
  academicPeriodId: number;
  courseCode: string;
  courseName: string;
  finalGrade: number;
  result: string;
  curriculumSemester: number;
}
export interface RuntimeConfig {
  apiUrl: string;
  keycloakUrl: string;
  keycloakRealm: string;
  keycloakClientId: string;
}
