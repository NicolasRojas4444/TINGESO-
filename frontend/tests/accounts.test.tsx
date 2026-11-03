import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ post: vi.fn(), put: vi.fn() }));
vi.mock("../app/service/http", async (original) => ({
  ...(await original<typeof import("../app/service/http")>()),
  api: mocks,
}));
import { StudentForm } from "../app/components/forms/StudentForm";
import { TeacherForm } from "../app/components/forms/TeacherForm";

beforeEach(() => { vi.clearAllMocks(); mocks.post.mockResolvedValue({ data: {} }); });

function fillAccount() {
  for (const [label, value] of [
    ["RUN", "12345678-5"], ["Nombre completo", "Test Person"],
    ["Correo electrónico", "person@example.test"], ["Nombre de usuario", "test-user"],
    ["Contraseña", " password123 "], ["Confirmar contraseña", " password123 "],
  ]) fireEvent.change(screen.getByLabelText(label), { target: { value } });
}

describe("administrator account forms", () => {
  it("keeps profile editing separate from credential creation", async () => {
    mocks.put.mockResolvedValue({ data: {} });
    const teacher = {
      id: 7, user: { run: "12345678-5", fullName: "Test Teacher", email: "teacher@example.test" },
      professionalTitle: "Engineer", academicDegree: "Bachelor", status: "ACTIVE",
    };
    render(<TeacherForm teacher={teacher as never} saved={vi.fn()} cancel={vi.fn()} />);
    expect(screen.queryByLabelText("Contraseña")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Guardar" }));
    await waitFor(() => expect(mocks.put).toHaveBeenCalledWith("/teachers/7", expect.objectContaining({ professionalTitle: "Engineer" })));
    expect(mocks.post).not.toHaveBeenCalled();
  });

  it("creates a student account without requiring a Keycloak ID", async () => {
    const saved = vi.fn();
    render(<StudentForm programs={[{ id: 1, name: "Program", status: "ACTIVE" } as never]} saved={saved} cancel={vi.fn()} />);
    fillAccount();
    fireEvent.change(screen.getByLabelText("Carrera"), { target: { value: "1" } });
    fireEvent.click(screen.getByRole("button", { name: "Guardar" }));
    await waitFor(() => expect(saved).toHaveBeenCalled());
    expect(mocks.post).toHaveBeenCalledWith("/users", expect.objectContaining({ role: "STUDENT", degreeProgramId: 1, password: " password123 " }));
    expect(screen.queryByLabelText(/ID de cuenta Keycloak/)).not.toBeInTheDocument();
  });

  it("creates a teacher with professional details", async () => {
    render(<TeacherForm saved={vi.fn()} cancel={vi.fn()} />);
    fillAccount();
    fireEvent.change(screen.getByLabelText("Título profesional"), { target: { value: "Engineer" } });
    fireEvent.change(screen.getByLabelText("Grado académico"), { target: { value: "Bachelor" } });
    fireEvent.click(screen.getByRole("button", { name: "Guardar" }));
    await waitFor(() => expect(mocks.post).toHaveBeenCalledWith("/users", expect.objectContaining({ role: "TEACHER", professionalTitle: "Engineer", academicDegree: "Bachelor" })));
  });

  it("rejects mismatched passwords without sending credentials", async () => {
    render(<TeacherForm saved={vi.fn()} cancel={vi.fn()} />);
    fillAccount();
    fireEvent.change(screen.getByLabelText("Confirmar contraseña"), { target: { value: "different-password" } });
    fireEvent.submit(screen.getByRole("button", { name: "Guardar" }).closest("form")!);
    expect(await screen.findByText("Las contraseñas no coinciden.")).toBeInTheDocument();
    expect(mocks.post).not.toHaveBeenCalled();
  });

  it("shows API rejection without reporting success", async () => {
    mocks.post.mockRejectedValue(new Error("The username or email is already registered"));
    const saved = vi.fn();
    render(<TeacherForm saved={saved} cancel={vi.fn()} />);
    fillAccount();
    fireEvent.submit(screen.getByRole("button", { name: "Guardar" }).closest("form")!);
    expect(await screen.findByText("The username or email is already registered")).toBeInTheDocument();
    expect(saved).not.toHaveBeenCalled();
  });
});
