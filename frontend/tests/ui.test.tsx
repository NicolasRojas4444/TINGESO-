import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ActionButton, FormPanel, TextField } from "../app/components/ui";
import { ApiError } from "../app/lib/api";

describe("forms and destructive actions", () => {
  it("retains form input when the server rejects a save", async () => {
    const save = vi
      .fn()
      .mockRejectedValue(new Error("The RUN is already registered"));
    render(
      <FormPanel title="Student" submit={save}>
        <TextField name="run" label="RUN" />
      </FormPanel>,
    );
    fireEvent.change(screen.getByLabelText("RUN"), {
      target: { value: "12345678-5" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Guardar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "The RUN is already registered",
    );
    expect(screen.getByLabelText("RUN")).toHaveValue("12345678-5");
    expect(save.mock.calls[0][0].get("run")).toBe("12345678-5");
  });
  it("does not delete when the user cancels confirmation", () => {
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const run = vi.fn();
    render(
      <ActionButton confirm="Delete?" run={run}>
        Eliminar
      </ActionButton>,
    );
    fireEvent.click(screen.getByText("Eliminar"));
    expect(run).not.toHaveBeenCalled();
    vi.restoreAllMocks();
  });
  it("disables pending actions and displays structured closing failures", async () => {
    let reject!: (reason: unknown) => void;
    const run = vi.fn().mockImplementation(
      () =>
        new Promise((_, fail) => {
          reject = fail;
        }),
    );
    render(<ActionButton run={run}>Cerrar período</ActionButton>);
    fireEvent.click(screen.getByText("Cerrar período"));
    expect(screen.getByText("Procesando…")).toBeDisabled();
    reject(
      new ApiError("Missing grades", 409, {
        code: "FINAL_GRADES_MISSING",
        sectionIds: [4, 8],
      }),
    );
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent("4, 8"),
    );
    expect(screen.getByText("Cerrar período")).toBeEnabled();
  });
});
