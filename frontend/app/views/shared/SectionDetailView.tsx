import { Link, useParams } from "react-router";
import { useAuth } from "~/auth/AuthProvider";
import { RequireRole } from "~/auth/RequireRole";
import { useResource } from "~/hooks/useResource";
import { ApiError } from "~/service/http";
import { sectionService } from "~/service/sectionService";
import { registrationService } from "~/service/registrationService";
import { gradeService } from "~/service/gradeService";
import { teacherService } from "~/service/teacherService";
import { academicPeriodService } from "~/service/academicPeriodService";
import { GradeRow } from "~/components/grades/GradeRow";
import { periodName, scheduleLabel } from "~/components/academic";
import { Badge, Notice, PageTitle, Resource, Table } from "~/components/ui";

export default function SectionDetailView() {
  return (
    <RequireRole roles={["ADMIN", "TEACHER"]}>
      <SectionContent />
    </RequireRole>
  );
}
function SectionContent() {
  const id = Number(useParams().id);
  const auth = useAuth();
  const resource = useResource(
    async (signal) => {
      const section = await sectionService.getById(id, signal);
      const [period, registrations, grades, teacher] = await Promise.all([
        academicPeriodService.getById(section.academicPeriodId, signal),
        registrationService.getBySection(id, signal),
        gradeService.getBySection(id, signal),
        auth.roles.includes("TEACHER")
          ? teacherService.getMe(signal).catch((error) => {
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
