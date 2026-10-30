import { useResource } from "../lib/hooks";
import { loadStudent } from "../lib/student-data";
import { Badge, PageTitle, Resource } from "../components/ui";

export default function StudentProfile() {
  const resource = useResource(loadStudent);
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
