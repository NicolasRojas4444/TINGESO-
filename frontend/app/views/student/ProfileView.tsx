import { useResource } from "~/hooks/useResource";
import { studentService } from "~/service/studentService";
import { Badge, PageTitle, Resource } from "~/components/ui";

export default function ProfileView() {
  const resource = useResource(studentService.getOverview);
  const data = resource.data;
  return (
    <>
      <PageTitle title="Mi información" />
      <Resource {...resource} retry={resource.reload}>
        {data && (
          <section className="panel">
            <h2>{data.student.user.fullName}</h2>
            <dl className="details">
              <dt>RUN</dt>
              <dd>{data.student.user.run}</dd>
              <dt>Correo</dt>
              <dd>{data.student.user.email}</dd>
              <dt>Carrera</dt>
              <dd>{data.program.name}</dd>
              <dt>Plan de estudios</dt>
              <dd>{data.plan.version}</dd>
              <dt>Estado académico</dt>
              <dd>
                <Badge value={data.student.academicStatus} />
              </dd>
            </dl>
            <p className="hint">
              Para corregir tus datos personales, contacta al administrador
              académico.
            </p>
          </section>
        )}
      </Resource>
    </>
  );
}
