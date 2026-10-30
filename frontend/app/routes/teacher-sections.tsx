import { useState } from "react";
import { Link } from "react-router";
import { RequireRole } from "../lib/auth";
import { get } from "../lib/api";
import { useResource } from "../lib/hooks";
import type { Period, Section, Teacher } from "../lib/types";
import { periodName, scheduleLabel } from "../components/academic";
import { Badge, Field, PageTitle, Resource, Table } from "../components/ui";

export default function TeacherSections() {
  return (
    <RequireRole roles={["TEACHER"]}>
      <Sections />
    </RequireRole>
  );
}
function Sections() {
  const resource = useResource(async (signal) => {
    const teacher = await get<Teacher>("/teachers/me", signal);
    const [sections, periods] = await Promise.all([
      get<Section[]>(`/sections/by-teacher/${teacher.id}`, signal),
      get<Period[]>("/academic-periods", signal),
    ]);
    return { teacher, sections, periods };
  });
  const [periodId, setPeriodId] = useState("");
  const data = resource.data;
  const rows =
    data?.sections.filter(
      (section) => !periodId || section.academicPeriodId === Number(periodId),
    ) || [];
  return (
    <>
      <PageTitle title="Mis secciones" />
      <Resource {...resource} retry={resource.reload}>
        {data && (
          <section className="panel">
            <Field label="Período académico">
              <select
                value={periodId}
                onChange={(event) => setPeriodId(event.target.value)}
              >
                <option value="">Todos los períodos</option>
                {data.periods.map((period) => (
                  <option key={period.id} value={period.id}>
                    {periodName(period)}
                  </option>
                ))}
              </select>
            </Field>
            <Table
              headers={[
                "Sección",
                "Período",
                "Asignatura",
                "Horario",
                "Estado",
                "Inscritos",
                "Acciones",
              ]}
              empty={!rows.length}
            >
              {rows.map((section) => {
                const period = data.periods.find(
                  (item) => item.id === section.academicPeriodId,
                );
                return (
                  <tr key={section.id}>
                    <td>#{section.id}</td>
                    <td>
                      {period ? periodName(period) : section.academicPeriodId}
                    </td>
                    <td>{section.courseName}</td>
                    <td>{scheduleLabel(section.scheduleBlocks)}</td>
                    <td>{period && <Badge value={period.status} />}</td>
                    <td>{section.registeredCount}</td>
                    <td>
                      <Link to={`/sections/${section.id}`}>
                        Ver estudiantes y notas
                      </Link>
                    </td>
                  </tr>
                );
              })}
            </Table>
          </section>
        )}
      </Resource>
    </>
  );
}
