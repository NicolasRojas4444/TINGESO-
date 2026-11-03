import type { Teacher } from "../../lib/types";
import { teacherService } from "../../service/teacherService";
import { text, userInput } from "../../utils/form";
import { FormPanel, TextField } from "../ui";
import { UserFields } from "./UserFields";
import { AccountFields } from "./AccountFields";
import { accountService } from "../../service/accountService";

export function TeacherForm({
  teacher,
  saved,
  cancel,
}: {
  teacher?: Teacher;
  saved: () => void;
  cancel: () => void;
}) {
  return (
    <FormPanel
      title={teacher ? "Editar docente" : "Registrar docente"}
      cancel={cancel}
      submit={async (form) => {
        const body = {
          user: userInput(form),
          professionalTitle: text(form, "professionalTitle"),
          academicDegree: text(form, "academicDegree"),
        };
        if (teacher) await teacherService.update(teacher.id, body);
        else await accountService.create(form, "TEACHER");
        saved();
      }}
    >
      <UserFields user={teacher?.user} />
      {!teacher && <AccountFields />}
      <TextField
        name="professionalTitle"
        label="Título profesional"
        value={teacher?.professionalTitle}
        maxLength={200}
      />
      <TextField
        name="academicDegree"
        label="Grado académico"
        value={teacher?.academicDegree}
        maxLength={200}
      />
    </FormPanel>
  );
}
