import { Link, useParams } from "react-router";
import { get } from "../lib/api";
import { useResource } from "../lib/hooks";
import type { Period, Section, Teacher } from "../lib/types";
import { Badge, PageTitle, Resource, Table } from "../components/ui";
import { periodName, scheduleLabel } from "../components/academic";

export default function TeacherDetail() {
  const { id } = useParams();
  const resource = useResource(
    async (signal) => {
      const [teacher, sections, periods] = await Promise.all([
        get<Teacher>(`/teachers/${id}`, signal),
        get<Section[]>(`/sections/by-teacher/${id}`, signal),
        get<Period[]>("/academic-periods", signal),
      ]);
      return { teacher, sections, periods };
    },
    [id],
  );
  const data = resource.data;
  return (
    <>
      <Link to="/admin/teachers">← Docentes</Link>
      <PageTitle title={data?.teacher.user.fullName || "Detalle del docente"} />
      <Resource {...resource} retry={resource.reload}>
        {data && (
          <>
            <section className="panel">
              <dl className="details">
                <dt>RUN</dt>
                <dd>{data.teacher.user.run}</dd>
                <dt>Correo</dt>
                <dd>{data.teacher.user.email}</dd>
                <dt>Título</dt>
                <dd>{data.teacher.professionalTitle}</dd>
                <dt>Grado</dt>
                <dd>{data.teacher.academicDegree}</dd>
                <dt>Estado</dt>
                <dd>
                  <Badge value={data.teacher.status} />
                </dd>
                <dt>ID Keycloak</dt>
                <dd>{data.teacher.user.keycloakSubject}</dd>
              </dl>
            </section>
            <section className="panel">
              <h2>Secciones asignadas</h2>
              <Table
                headers={["Período", "Asignatura", "Horario", "Acciones"]}
                empty={!data.sections.length}
              >
                {data.sections.map((section) => (
                  <tr key={section.id}>
                    <td>
                      {data.periods.find(
                        (period) => period.id === section.academicPeriodId,
                      )
                        ? periodName(
                            data.periods.find(
                              (period) =>
                                period.id === section.academicPeriodId,
                            )!,
                          )
                        : section.academicPeriodId}
                    </td>
                    <td>{section.courseName}</td>
                    <td>{scheduleLabel(section.scheduleBlocks)}</td>
                    <td>
                      <Link to={`/sections/${section.id}`}>
                        Ver estudiantes y notas
                      </Link>
                    </td>
                  </tr>
                ))}
              </Table>
            </section>
          </>
        )}
      </Resource>
    </>
  );
}
