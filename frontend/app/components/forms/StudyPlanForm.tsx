import type { Plan } from "../../lib/types";
import { studyPlanService } from "../../service/studyPlanService";
import { text } from "../../utils/form";
import { Field, FormPanel, TextField } from "../ui";

export function StudyPlanForm({
  plan,
  programId,
  saved,
  cancel,
}: {
  plan?: Plan;
  programId: number;
  saved: () => void;
  cancel: () => void;
}) {
  return (
    <FormPanel
      title={plan ? "Editar versión" : "Crear plan de estudios"}
      cancel={cancel}
      submit={async (form) => {
        const version = text(form, "version");
        if (plan) await studyPlanService.update(plan.id, version);
        else {
          const status = text(form, "status");
          if (
            status === "CURRENT" &&
            !window.confirm(
              "¿Establecer el nuevo plan como vigente? El plan vigente anterior pasará a no vigente; los estudiantes conservarán su plan.",
            )
          )
            return;
          await studyPlanService.create({
            degreeProgramId: programId,
            version,
            status,
          });
        }
        saved();
      }}
    >
      <TextField
        name="version"
        label="Versión / identificador"
        maxLength={30}
        value={plan?.version}
      />
      {!plan && (
        <Field label="Estado inicial">
          <select name="status">
            <option value="NON_CURRENT">No vigente</option>
            <option value="CURRENT">Vigente</option>
          </select>
        </Field>
      )}
    </FormPanel>
  );
}
