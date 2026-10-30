import { useState } from "react";
import { Link, useParams } from "react-router";
import { api, get } from "../lib/api";
import { useResource } from "../lib/hooks";
import type { Course, Period, Section, Teacher } from "../lib/types";
import { periodName, scheduleLabel } from "../components/academic";
import { SectionForm } from "../components/section-form";
import {
  ActionButton,
  Badge,
  Notice,
  PageTitle,
  Resource,
  Table,
} from "../components/ui";

export default function PeriodDetail() {
  const { id } = useParams();
  const resource = useResource(
    async (signal) => {
      const [period, sections, courses, teachers] = await Promise.all([
        get<Period>(`/academic-periods/${id}`, signal),
        get<Section[]>(`/sections/by-period/${id}`, signal),
        get<Course[]>("/courses", signal),
        get<Teacher[]>("/teachers", signal),
      ]);
      return { period, sections, courses, teachers };
    },
    [id],
  );
  const [editing, setEditing] = useState<Section | "new" | null>(null);
  const [success, setSuccess] = useState("");
  const data = resource.data;
  return (
    <>
      <Link to="/admin/periods">← Períodos</Link>
      <PageTitle
        title={
          data
            ? `Oferta académica · ${periodName(data.period)}`
            : "Oferta académica"
        }
      >
        {data?.period.status === "OPEN" && (
          <button className="primary" onClick={() => setEditing("new")}>
            Nueva sección
          </button>
        )}
      </PageTitle>
      <Notice kind="success">{success}</Notice>
      <Resource {...resource} retry={resource.reload}>
        {data && (
          <>
            <Badge value={data.period.status} />
            {data.period.status === "OPEN" && editing && (
              <SectionForm
                key={editing === "new" ? "new" : editing.id}
                section={editing === "new" ? undefined : editing}
                courses={data.courses.filter(
                  (course) =>
                    !data.sections.some(
                      (section) => section.courseId === course.id,
                    ),
                )}
                teachers={data.teachers}
                periodId={Number(id)}
                cancel={() => setEditing(null)}
                saved={() => {
                  setEditing(null);
                  setSuccess("Sección guardada correctamente.");
                  resource.reload();
                }}
              />
            )}
            <section className="panel">
              <Table
                headers={[
                  "Sección",
                  "Asignatura",
                  "Docente",
                  "Inscritos / capacidad",
                  "Cupos",
                  "Horario",
                  "Acciones",
                ]}
                empty={!data.sections.length}
              >
                {data.sections.map((section) => (
                  <tr key={section.id}>
                    <td>#{section.id}</td>
                    <td>{section.courseName}</td>
                    <td>{section.teacherName}</td>
                    <td>
                      {section.registeredCount} / {section.capacity}
                    </td>
                    <td>{section.availableSeats}</td>
                    <td>{scheduleLabel(section.scheduleBlocks)}</td>
                    <td>
                      <div className="actions">
                        <Link to={`/sections/${section.id}`}>
                          Estudiantes y notas
                        </Link>
                        {data.period.status === "OPEN" && (
                          <>
                            <button onClick={() => setEditing(section)}>
                              Editar
                            </button>
                            <ActionButton
                              danger
                              confirm={`¿Eliminar la sección #${section.id}?`}
                              run={() => api.delete(`/sections/${section.id}`)}
                              onSuccess={() => {
                                setEditing(null);
                                resource.reload();
                              }}
                            >
                              Eliminar
                            </ActionButton>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </Table>
            </section>
            {data.period.status === "OPEN" ? (
              <section className="panel">
                <h2>Cierre del período</h2>
                <p>
                  Consolida las notas y actualiza la situación académica de los
                  estudiantes. Esta acción es definitiva y exige que todas las
                  inscripciones activas tengan nota final.
                </p>
                <ActionButton
                  danger
                  confirm={`¿Cerrar definitivamente el período ${periodName(data.period)}? No podrás modificar sus notas ni inscripciones.`}
                  run={() => api.post(`/academic-periods/${id}/close`)}
                  onSuccess={() => {
                    setEditing(null);
                    setSuccess(
                      "Período cerrado. Historial y estados académicos actualizados.",
                    );
                    resource.reload();
                  }}
                >
                  Cerrar período
                </ActionButton>
              </section>
            ) : (
              <Notice kind="info">
                Este período está cerrado: la oferta, las inscripciones y las
                notas se muestran solo para consulta.
              </Notice>
            )}
          </>
        )}
      </Resource>
    </>
  );
}
