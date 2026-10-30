import { useState } from "react";
import { api, errorMessage, get } from "../lib/api";
import { useResource } from "../lib/hooks";
import type { Period, Registration, Section, Student } from "../lib/types";
import { periodName, scheduleLabel } from "../components/academic";
import {
  ActionButton,
  Badge,
  Field,
  Notice,
  PageTitle,
  Resource,
  Table,
} from "../components/ui";

export default function StudentRegistration() {
  const resource = useResource(async (signal) => {
    const [student, periods] = await Promise.all([
      get<Student>("/students/me", signal),
      get<Period[]>("/academic-periods", signal),
    ]);
    return {
      student,
      periods: periods
        .filter((period) => period.status === "OPEN")
        .sort((a, b) => b.year - a.year || b.semester - a.semester),
    };
  });
  const [periodId, setPeriodId] = useState("");
  const data = resource.data;
  const selectedPeriod =
    data?.periods.find((period) => period.id === Number(periodId)) ||
    data?.periods[0];
  return (
    <>
      <PageTitle title="Inscripción académica" />
      <Resource {...resource} retry={resource.reload}>
        {data && (
          <>
            <section className="panel">
              <p>
                {data.student.user.fullName} ·{" "}
                <Badge value={data.student.academicStatus} />
              </p>
              {data.student.academicStatus !== "REGULAR" && (
                <Notice>
                  Solo los estudiantes REGULARES pueden guardar inscripciones.
                </Notice>
              )}
              <Field label="Período abierto">
                <select
                  value={selectedPeriod?.id || ""}
                  onChange={(event) => setPeriodId(event.target.value)}
                >
                  {!data.periods.length && (
                    <option value="">Sin períodos abiertos</option>
                  )}
                  {data.periods.map((period) => (
                    <option key={period.id} value={period.id}>
                      {periodName(period)}
                    </option>
                  ))}
                </select>
              </Field>
              <p className="hint">
                Debes estar matriculado en este período. La matrícula se carga
                fuera del sistema. El backend valida cupos, horarios,
                prerrequisitos y prioridad académica al guardar.
              </p>
            </section>
            {selectedPeriod ? (
              <Selection
                key={`${data.student.id}-${selectedPeriod.id}`}
                student={data.student}
                period={selectedPeriod}
              />
            ) : (
              <Notice kind="info">
                No hay períodos abiertos para inscribir asignaturas.
              </Notice>
            )}
          </>
        )}
      </Resource>
    </>
  );
}
function Selection({ student, period }: { student: Student; period: Period }) {
  const [success, setSuccess] = useState("");
  const resource = useResource(
    async (signal) => {
      const [options, registrations, sections] = await Promise.all([
        get<Section[]>(
          `/registrations/replacement-options?studentId=${student.id}&periodId=${period.id}`,
          signal,
        ),
        get<Registration[]>(`/registrations/by-student/${student.id}`, signal),
        get<Section[]>(`/sections/by-period/${period.id}`, signal),
      ]);
      const active = registrations.filter(
        (registration) =>
          registration.academicPeriodId === period.id &&
          registration.status === "ACTIVE",
      );
      // Preserve current sections even when a later catalog change makes them ineligible.
      const merged = [...options];
      active.forEach((registration) => {
        if (!merged.some((section) => section.id === registration.sectionId)) {
          const section = sections.find(
            (item) => item.id === registration.sectionId,
          );
          if (section) merged.push(section);
        }
      });
      return { options: merged, active };
    },
    [student.id, period.id],
  );
  return (
    <>
      <Notice kind="success">{success}</Notice>
      <Resource {...resource} retry={resource.reload}>
        {resource.data && (
          <SelectionEditor
            key={
              resource.data.active.map((item) => item.id).join(",") +
              resource.data.options
                .map((item) => `${item.id}:${item.availableSeats}`)
                .join(",")
            }
            student={student}
            period={period}
            options={resource.data.options}
            active={resource.data.active}
            reload={resource.reload}
            saved={() => {
              setSuccess("Selección guardada correctamente.");
              resource.reload();
            }}
          />
        )}
      </Resource>
    </>
  );
}
function SelectionEditor({
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
      const path = `/registrations?studentId=${student.id}&periodId=${period.id}`;
      if (active.length) await api.put(path, { sectionIds: selected });
      else
        await api.post(
          path,
          selected.map((sectionId) => ({ sectionId })),
        );
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
        completar el plan, el backend aplica la excepción. Desmarca y marca
        alternativas antes de guardar para reemplazar tu selección completa.
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
            {options.map((section) => {
              const existing = active.find(
                (registration) => registration.sectionId === section.id,
              );
              return (
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
                  <td>{existing ? "Inscrita actualmente" : "Disponible"}</td>
                </tr>
              );
            })}
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
                    run={() => api.delete(`/registrations/${registration.id}`)}
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
