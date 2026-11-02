import type { Program, Student } from "../../lib/types";
import { studentService } from "../../service/studentService";
import { number, userInput } from "../../utils/form";
import { Field, FormPanel } from "../ui";
import { UserFields } from "./UserFields";

export function StudentForm({
  student,
  programs,
  saved,
  cancel,
}: {
  student?: Student;
  programs: Program[];
  saved: () => void;
  cancel: () => void;
}) {
  return (
    <FormPanel
      title={student ? "Editar estudiante" : "Registrar estudiante"}
      cancel={cancel}
      submit={async (form) => {
        const user = userInput(form);
        if (student) await studentService.update(student.id, { user });
        else
          await studentService.create({
            user,
            degreeProgramId: number(form, "degreeProgramId"),
          });
        saved();
      }}
    >
      <UserFields user={student?.user} creating={!student} />
      {!student && (
        <Field label="Carrera">
          <select name="degreeProgramId" required defaultValue="">
            <option value="" disabled>
              Selecciona una carrera
            </option>
            {programs
              .filter((program) => program.status === "ACTIVE")
              .map((program) => (
                <option key={program.id} value={program.id}>
                  {program.name}
                </option>
              ))}
          </select>
        </Field>
      )}
    </FormPanel>
  );
}
