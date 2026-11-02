import type { Program } from "../../lib/types";
import { programService } from "../../service/programService";
import { text } from "../../utils/form";
import { Field, FormPanel, TextField } from "../ui";

export function ProgramForm({
  program,
  saved,
  cancel,
}: {
  program?: Program;
  saved: () => void;
  cancel: () => void;
}) {
  return (
    <FormPanel
      title={program ? "Editar carrera" : "Crear carrera"}
      cancel={cancel}
      submit={async (form) => {
        const input = {
          code: text(form, "code"),
          name: text(form, "name"),
          description: text(form, "description"),
        };
        if (program) await programService.update(program.id, input);
        else await programService.create(input);
        saved();
      }}
    >
      <TextField
        name="code"
        label="Código"
        value={program?.code}
        maxLength={30}
      />
      <TextField
        name="name"
        label="Nombre"
        value={program?.name}
        maxLength={200}
      />
      <Field label="Descripción">
        <textarea
          name="description"
          defaultValue={program?.description}
          required
          maxLength={2000}
        />
      </Field>
    </FormPanel>
  );
}
