import { useState } from "react";
import type { Grade, Registration } from "../../lib/types";
import { gradeService } from "../../service/gradeService";
import { number } from "../../utils/form";
import { Badge, FormPanel, Notice, TextField } from "../ui";

export function GradeRow({
  registration,
  initial,
  editable,
}: {
  registration: Registration;
  initial?: Grade;
  editable: boolean;
}) {
  const [grade, setGrade] = useState(initial);
  const [success, setSuccess] = useState("");
  return (
    <tr>
      <td>
        {registration.studentName}
        <small>
          Estudiante #{registration.studentId} · Inscripción #{registration.id}
        </small>
      </td>
      <td>
        {editable ? (
          <FormPanel
            title=""
            label={grade ? "Actualizar nota" : "Guardar nota"}
            submit={async (form) => {
              const value = number(form, "value");
              const result = grade
                ? await gradeService.update(registration.id, value)
                : await gradeService.create(registration.id, value);
              setGrade(result);
              setSuccess("Nota guardada.");
            }}
          >
            <TextField
              name="value"
              label={`Nota de ${registration.studentName}`}
              type="number"
              min={1}
              max={7}
              step="0.1"
              value={grade?.value}
            />
          </FormPanel>
        ) : grade ? (
          grade.value.toFixed(1)
        ) : (
          "Sin nota"
        )}
      </td>
      <td>
        {grade ? <Badge value={grade.result} /> : "Pendiente"}
        <Notice kind="success">{success}</Notice>
      </td>
    </tr>
  );
}
