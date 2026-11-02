import type { Teacher } from "../../lib/types";
import { teacherService } from "../../service/teacherService";
import { text, userInput } from "../../utils/form";
import { FormPanel, TextField } from "../ui";
import { UserFields } from "./UserFields";

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
        else await teacherService.create(body);
        saved();
      }}
    >
      <UserFields user={teacher?.user} creating={!teacher} />
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
