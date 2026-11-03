import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("../app/service/http", async (original) => ({
  ...(await original<typeof import("../app/service/http")>()),
  get: mocks.get,
}));
import { Sidebar } from "../app/components/layout/Sidebar";
import { AccountFields } from "../app/components/forms/AccountFields";
import StudentDetailView from "../app/views/admin/StudentDetailView";
import TeacherDetailView from "../app/views/admin/TeacherDetailView";
import { ErrorBoundary } from "../app/root";

const user = {
  id: 1, keycloakSubject: "private-identity-not-for-display",
  run: "12345678-5", fullName: "Test Person", email: "person@example.test",
};
beforeEach(() => {
  mocks.get.mockImplementation(async (path: string) => {
    if (path === "/students/1") return { id: 1, user, studyPlanId: 1, degreeProgramId: 1, academicStatus: "REGULAR" };
    if (path === "/teachers/1") return { id: 1, user, professionalTitle: "Engineer", academicDegree: "Bachelor", status: "ACTIVE" };
    return [];
  });
});

describe("user-facing application presentation", () => {
  it("uses application branding in navigation and account forms", () => {
    const { container } = render(
      <MemoryRouter>
        <Sidebar role="ADMIN" roles={["ADMIN"]} />
        <AccountFields />
      </MemoryRouter>,
    );
    expect(screen.getByText("SIGA IPT · Gestión académica")).toBeInTheDocument();
    expect(container.textContent).not.toMatch(/keycloak|backend|frontend/i);
  });

  it.each([
    ["/admin/students/1", "/admin/students/:id", <StudentDetailView />],
    ["/admin/teachers/1", "/admin/teachers/:id", <TeacherDetailView />],
  ] as const)("does not render technical identity data on %s", async (path, route, element) => {
    const { container } = render(
      <MemoryRouter initialEntries={[path]}>
        <Routes><Route path={route} element={element} /></Routes>
      </MemoryRouter>,
    );
    await screen.findByText("person@example.test");
    expect(container.textContent).not.toMatch(/keycloak|backend|frontend/i);
    expect(container.textContent).not.toContain(user.keycloakSubject);
  });

  it("does not display raw exceptions or stack traces", () => {
    const error = new Error("Keycloak backend initialization failed");
    const { container } = render(<ErrorBoundary error={error} params={{}} />);
    expect(screen.getByText("Ocurrió un error inesperado.")).toBeInTheDocument();
    expect(container.querySelector("pre")).toBeNull();
    expect(container.textContent).not.toMatch(/keycloak|backend|frontend/i);
  });
});
