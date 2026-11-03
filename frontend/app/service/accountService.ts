import { api } from "./http";
import { text } from "../utils/form";

export const accountService = {
  create: async (form: FormData, role: "STUDENT" | "TEACHER") => {
    const password = String(form.get("password") || "");
    if (password !== String(form.get("confirmPassword") || "")) {
      throw new Error("Las contraseñas no coinciden.");
    }
    await api.post("/users", {
      username: text(form, "username"),
      password,
      run: text(form, "run"),
      fullName: text(form, "fullName"),
      email: text(form, "email"),
      role,
      ...(role === "STUDENT"
        ? { degreeProgramId: Number(form.get("degreeProgramId")) }
        : {
            professionalTitle: text(form, "professionalTitle"),
            academicDegree: text(form, "academicDegree"),
          }),
    });
  },
};
