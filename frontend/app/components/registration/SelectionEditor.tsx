import { useState } from "react";
import type { Period, Registration, Section, Student } from "../../lib/types";
import { errorMessage } from "../../service/http";
import { registrationService } from "../../service/registrationService";
import { scheduleLabel } from "../academic";
import { ActionButton, Badge, Notice, Table } from "../ui";

export function SelectionEditor({
  student,
  period,
  options,
  active,
  reload,
  saved,
}: {
  student: Student;
  period: Period;
  options: Section[];
  active: Registration[];
  reload: () => void;
  saved: () => void;
}) {
  const [selected, setSelected] = useState<number[]>(
    active.map((registration) => registration.sectionId),
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const canEdit = student.academicStatus === "REGULAR";
  async function save() {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      if (active.length)
        await registrationService.replace(student.id, period.id, selected);
      else await registrationService.register(student.id, period.id, selected);
      saved();
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Notice kind="info">
        Selecciona entre 3 y 6 asignaturas. Si te quedan menos de tres para
        completar el plan, podrás inscribir las pendientes disponibles. Desmarca
        y marca alternativas antes de guardar para reemplazar tu selección completa.
      </Notice>
      <section className="panel">
        <div className="page-title">
          <h2>Selección: {selected.length} asignaturas</h2>
          <button
            disabled={busy}
            onClick={() => {
              if (
                window.confirm(
                  "¿Actualizar cupos y descartar los cambios sin guardar?",
                )
              )
                reload();
            }}
          >
            Actualizar cupos
          </button>
        </div>
        <Notice>{error}</Notice>
        <fieldset disabled={busy || !canEdit}>
          <Table
            headers={[
              "Seleccionar",
              "Asignatura",
              "Docente",
              "Horario",
              "Cupos",
              "Situación",
            ]}
            empty={!options.length}
          >
            {options.map((section) => (
              <tr key={section.id}>
                <td>
                  <input
                    aria-label={`Seleccionar ${section.courseName}`}
                    type="checkbox"
                    checked={selected.includes(section.id)}
                    disabled={
                      !selected.includes(section.id) && selected.length >= 6
                    }
                    onChange={(event) =>
                      setSelected((previous) =>
                        event.target.checked
                          ? [...previous, section.id]
                          : previous.filter((item) => item !== section.id),
                      )
                    }
                  />
                </td>
                <td>
                  {section.courseName}
                  <small>Sección #{section.id}</small>
                </td>
                <td>{section.teacherName}</td>
                <td>{scheduleLabel(section.scheduleBlocks)}</td>
                <td>{section.availableSeats}</td>
                <td>
                  {active.some(
                    (registration) => registration.sectionId === section.id,
                  )
                    ? "Inscrita actualmente"
                    : "Disponible"}
                </td>
              </tr>
            ))}
          </Table>
          <button
            className="primary"
            disabled={!selected.length}
            onClick={save}
          >
            {busy ? "Guardando…" : "Guardar selección"}
          </button>
        </fieldset>
      </section>
      {active.length > 0 && (
        <section className="panel">
          <h2>Inscripciones actuales</h2>
          <p className="hint">
            La anulación individual también valida carga mínima y prioridad de
            semestres. Para intercambiar asignaturas, usa la selección completa
            de arriba.
          </p>
          <Table headers={["Asignatura", "Estado", "Acción"]}>
            {active.map((registration) => (
              <tr key={registration.id}>
                <td>{registration.courseName}</td>
                <td>
                  <Badge value={registration.status} />
                </td>
                <td>
                  <ActionButton
                    disabled={busy || !canEdit}
                    danger
                    confirm={`¿Anular ${registration.courseName}? También se eliminará su nota provisional, si existe.`}
                    run={() => registrationService.cancel(registration.id)}
                    onSuccess={reload}
                  >
                    Anular inscripción
                  </ActionButton>
                </td>
              </tr>
            ))}
          </Table>
        </section>
      )}
    </>
  );
}
