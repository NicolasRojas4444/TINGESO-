import { useState } from "react";
import { useResource } from "~/hooks/useResource";
import { studentService } from "~/service/studentService";
import { academicPeriodService } from "~/service/academicPeriodService";
import { RegistrationSelection } from "~/components/registration/RegistrationSelection";
import { periodName } from "~/components/academic";
import { Badge, Field, Notice, PageTitle, Resource } from "~/components/ui";

export default function RegistrationView() {
  const resource = useResource(async (signal) => {
    const [student, periods] = await Promise.all([
      studentService.getMe(signal),
      academicPeriodService.getAll(signal),
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
              <RegistrationSelection
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
