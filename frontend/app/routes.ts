import {
  type RouteConfig,
  index,
  route,
  layout,
} from "@react-router/dev/routes";

export default [
  index("routes/home.tsx"),
  layout("routes/workspace.tsx", [
    route("admin", "routes/admin.tsx", [
      route("students", "routes/students.tsx"),
      route("students/:id", "routes/student-detail.tsx"),
      route("teachers", "routes/teachers.tsx"),
      route("teachers/:id", "routes/teacher-detail.tsx"),
      route("programs", "routes/programs.tsx"),
      route("programs/:id", "routes/program-detail.tsx"),
      route("plans/:id", "routes/plan-detail.tsx"),
      route("periods", "routes/periods.tsx"),
      route("periods/:id", "routes/period-detail.tsx"),
    ]),
    route("teacher/sections", "routes/teacher-sections.tsx"),
    route("sections/:id", "routes/section-detail.tsx"),
    route("student", "routes/student.tsx", [
      route("profile", "routes/student-profile.tsx"),
      route("plan", "routes/student-plan.tsx"),
      route("registration", "routes/student-registration.tsx"),
      route("history", "routes/student-history.tsx"),
    ]),
  ]),
] satisfies RouteConfig;
