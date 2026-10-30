import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Section } from "../app/lib/types";

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}));
vi.mock("../app/lib/api", async (original) => ({
  ...(await original<typeof import("../app/lib/api")>()),
  get: mocks.get,
  api: { post: mocks.post, put: mocks.put, delete: mocks.delete },
}));
import StudentRegistration from "../app/routes/student-registration";

const student = {
  id: 1,
  user: { fullName: "Test Student" },
  academicStatus: "REGULAR",
};
const sections: Section[] = [1, 2, 3, 4].map((id) => ({
  id,
  courseId: id,
  academicPeriodId: 1,
  teacherId: 1,
  courseName: `Course ${id}`,
  teacherName: "Teacher",
  capacity: 30,
  registeredCount: id < 4 ? 1 : 0,
  availableSeats: id < 4 ? 29 : 30,
  scheduleBlocks: [{ dayOfWeek: "MONDAY", module: id }],
}));
let active = [1, 2, 3].map((id) => ({
  id,
  sectionId: id,
  academicPeriodId: 1,
  status: "ACTIVE",
  courseName: `Course ${id}`,
}));
beforeEach(() => {
  active = [1, 2, 3].map((id) => ({
    id,
    sectionId: id,
    academicPeriodId: 1,
    status: "ACTIVE",
    courseName: `Course ${id}`,
  }));
  mocks.get.mockImplementation(async (path: string) => {
    if (path === "/students/me") return student;
    if (path === "/academic-periods")
      return [{ id: 1, year: 2026, semester: 2, status: "OPEN" }];
    if (path.startsWith("/registrations/by-student")) return active;
    return sections;
  });
});
describe("complete registration selection", () => {
  it("keeps the success confirmation after reloading the server selection", async () => {
    mocks.put.mockResolvedValue({ data: [] });
    render(<StudentRegistration />);
    await screen.findByLabelText("Seleccionar Course 1");
    fireEvent.click(screen.getByRole("button", { name: "Guardar selección" }));
    expect(
      await screen.findByText("Selección guardada correctamente."),
    ).toBeInTheDocument();
    await screen.findByLabelText("Seleccionar Course 1");
    expect(
      screen.getByText("Selección guardada correctamente."),
    ).toBeInTheDocument();
  });
  it("submits one atomic replacement and preserves edits on rejection", async () => {
    mocks.put.mockRejectedValue(new Error("Section has no available seats"));
    render(<StudentRegistration />);
    await screen.findByLabelText("Seleccionar Course 1");
    fireEvent.click(screen.getByLabelText("Seleccionar Course 1"));
    fireEvent.click(screen.getByLabelText("Seleccionar Course 4"));
    fireEvent.click(screen.getByRole("button", { name: "Guardar selección" }));
    await waitFor(() =>
      expect(mocks.put).toHaveBeenCalledWith(
        "/registrations?studentId=1&periodId=1",
        { sectionIds: [2, 3, 4] },
      ),
    );
    expect(
      await screen.findByText("Section has no available seats"),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Seleccionar Course 4")).toBeChecked();
    expect(screen.getByLabelText("Seleccionar Course 1")).not.toBeChecked();
    expect(mocks.delete).not.toHaveBeenCalled();
  });
  it("uses POST for an initial registration", async () => {
    active = [];
    mocks.post.mockRejectedValue(new Error("Student is not enrolled"));
    render(<StudentRegistration />);
    await screen.findByLabelText("Seleccionar Course 1");
    for (const id of [1, 2, 3])
      fireEvent.click(screen.getByLabelText(`Seleccionar Course ${id}`));
    fireEvent.click(screen.getByRole("button", { name: "Guardar selección" }));
    await waitFor(() =>
      expect(mocks.post).toHaveBeenCalledWith(
        "/registrations?studentId=1&periodId=1",
        [{ sectionId: 1 }, { sectionId: 2 }, { sectionId: 3 }],
      ),
    );
    expect(
      await screen.findByText("Student is not enrolled"),
    ).toBeInTheDocument();
  });
});
