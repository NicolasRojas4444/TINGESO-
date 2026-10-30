import { useState } from "react";
import { Link, useParams } from "react-router";
import { ApiError, api, get } from "../lib/api";
import { RequireRole, useAuth } from "../lib/auth";
import { useResource } from "../lib/hooks";
import type {
  Grade,
  Period,
  Registration,
  Section,
  Teacher,
} from "../lib/types";
import { periodName, scheduleLabel } from "../components/academic";
import {
  Badge,
  FormPanel,
  Notice,
  PageTitle,
  Resource,
  Table,
  TextField,
  number,
} from "../components/ui";

export default function SectionDetail() {
  return (
    <RequireRole roles={["ADMIN", "TEACHER"]}>
      <SectionContent />
    </RequireRole>
  );
}
function SectionContent() {
  const { id } = useParams();
  const auth = useAuth();
  const resource = useResource(
    async (signal) => {
      const section = await get<Section>(`/sections/${id}`, signal);
      const [period, registrations, grades, teacher] = await Promise.all([
        get<Period>(`/academic-periods/${section.academicPeriodId}`, signal),
        get<Registration[]>(`/registrations/by-section/${id}`, signal),
        get<Grade[]>(`/final-grades/by-section/${id}`, signal),
        auth.roles.includes("TEACHER")
          ? get<Teacher>("/teachers/me", signal).catch((error) => {
              if (
                error instanceof ApiError &&
                error.status === 404 &&
                auth.roles.includes("ADMIN")
              )
                return null;
              throw error;
            })
          : Promise.resolve(null),
      ]);
      return { section, period, registrations, grades, teacher };
    },
    [id],
  );
  const data = resource.data;
  return (
    <>
      <Link
        to={
          auth.roles.includes("ADMIN") && data
            ? `/admin/periods/${data.period.id}`
            : "/teacher/sections"
        }
      >
        ← Volver a las secciones
      </Link>
      <PageTitle
        title={
          data
            ? `${data.section.courseName} · Sección #${data.section.id}`
            : "Estudiantes y notas"
        }
      />
      <Resource {...resource} retry={resource.reload}>
        {data && (
          <>
            <section className="panel">
              <dl className="details">
                <dt>Período</dt>
                <dd>
                  {periodName(data.period)} ·{" "}
                  <Badge value={data.period.status} />
                </dd>
                <dt>Docente</dt>
                <dd>{data.section.teacherName}</dd>
                <dt>Horario</dt>
                <dd>{scheduleLabel(data.section.scheduleBlocks)}</dd>
                <dt>Inscritos / capacidad</dt>
                <dd>
                  {data.section.registeredCount} / {data.section.capacity}
                </dd>
              </dl>
            </section>
            <Notice kind="info">
              {data.period.status === "CLOSED"
                ? "Período cerrado: las notas son de solo lectura."
                : data.teacher?.id === data.section.teacherId
                  ? "Ingresa una nota final entre 1,0 y 7,0, con un decimal. Guarda cada fila por separado."
                  : "Solo el docente responsable puede ingresar o modificar notas. Esta vista es de consulta."}
            </Notice>
            <section className="panel">
              <Table
                headers={["Estudiante", "Nota final", "Resultado / acción"]}
                empty={!data.registrations.length}
              >
                {data.registrations.map((registration) => (
                  <GradeRow
                    key={registration.id}
                    registration={registration}
                    initial={data.grades.find(
                      (grade) => grade.registrationId === registration.id,
                    )}
                    editable={
                      data.period.status === "OPEN" &&
                      data.teacher?.id === data.section.teacherId
                    }
                  />
                ))}
              </Table>
            </section>
          </>
        )}
      </Resource>
    </>
  );
}
function GradeRow({
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
              const body = { value: number(form, "value") };
              const response = grade
                ? await api.put<Grade>(
                    `/final-grades/by-registration/${registration.id}`,
                    body,
                  )
                : await api.post<Grade>(
                    `/final-grades/by-registration/${registration.id}`,
                    body,
                  );
              setGrade(response.data);
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
