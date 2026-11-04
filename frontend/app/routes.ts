import {
  type RouteConfig,
  index,
  route,
  layout,
} from "@react-router/dev/routes";

export default [
  index("views/LoginView.tsx"),
  layout("layouts/WorkspaceLayout.tsx", [
    route("admin", "layouts/AdminLayout.tsx", [
      route("students", "views/admin/StudentsView.tsx"),
      route("students/:id", "views/admin/StudentDetailView.tsx"),
      route("teachers", "views/admin/TeachersView.tsx"),
      route("teachers/:id", "views/admin/TeacherDetailView.tsx"),
      route("programs", "views/admin/ProgramsView.tsx"),
      route("programs/:id", "views/admin/ProgramDetailView.tsx"),
      route("plans/:id", "views/admin/PlanDetailView.tsx"),
      route("periods", "views/admin/PeriodsView.tsx"),
      route("periods/:id", "views/admin/PeriodDetailView.tsx"),
    ]),
    route("teacher/sections", "views/teacher/SectionsView.tsx"),
    route("sections/:id", "views/shared/SectionDetailView.tsx"),
    route("student", "layouts/StudentLayout.tsx", [
      route("profile", "views/student/ProfileView.tsx"),
      route("plan", "views/student/PlanView.tsx"),
      route("schedule", "views/student/ScheduleView.tsx"),
      route("registration", "views/student/RegistrationView.tsx"),
      route("history", "views/student/HistoryView.tsx"),
    ]),
  ]),
] satisfies RouteConfig;
