import { Link, useParams } from "react-router";
import { api, get } from "../lib/api";
import { useResource } from "../lib/hooks";
import type {
  AcademicRecord,
  Period,
  Plan,
  Program,
  Registration,
  Student,
} from "../lib/types";
import { History, periodName } from "../components/academic";
import {
  Badge,
  Field,
  FormPanel,
  PageTitle,
  Resource,
  Table,
  number,
  text,
} from "../components/ui";

export default function StudentDetail() {
  const { id } = useParams();
  const resource = useResource(
    async (signal) => {
      const [student, records, registrations, programs, plans, periods] =
        await Promise.all([
          get<Student>(`/students/${id}`, signal),
          get<AcademicRecord[]>(`/students/${id}/history`, signal),
          get<Registration[]>(`/registrations/by-student/${id}`, signal),
          get<Program[]>("/degree-programs", signal),
          get<Plan[]>("/study-plans", signal),
          get<Period[]>("/academic-periods", signal),
        ]);
      return { student, records, registrations, programs, plans, periods };
    },
    [id],
  );
  const data = resource.data;
  return (
    <>
      <Link to="/admin/students">← Estudiantes</Link>
      <PageTitle
        title={data?.student.user.fullName || "Detalle del estudiante"}
      />
      <Resource {...resource} retry={resource.reload}>
        {data && (
          <>
            <section className="panel">
              <h2>Información académica</h2>
              <dl className="details">
                <dt>RUN</dt>
                <dd>{data.student.user.run}</dd>
                <dt>Correo</dt>
                <dd>{data.student.user.email}</dd>
                <dt>Carrera</dt>
                <dd>
                  {
                    data.programs.find(
                      (item) => item.id === data.student.degreeProgramId,
                    )?.name
                  }
                </dd>
                <dt>Plan</dt>
                <dd>
                  {
                    data.plans.find(
                      (item) => item.id === data.student.studyPlanId,
                    )?.version
                  }
                </dd>
                <dt>Estado</dt>
                <dd>
                  <Badge value={data.student.academicStatus} />
                </dd>
                <dt>ID Keycloak</dt>
                <dd>{data.student.user.keycloakSubject}</dd>
              </dl>
            </section>
            {!["GRADUATED", "ELIMINATED"].includes(
              data.student.academicStatus,
            ) && (
              <FormPanel
                title="Cambiar estado académico"
                submit={async (form) => {
                  await api.put(`/students/${id}/status`, {
                    academicStatus: text(form, "status"),
                  });
                  resource.reload();
                }}
              >
                <Field label="Estado">
                  <select
                    name="status"
                    defaultValue={data.student.academicStatus}
                  >
                    <option value="REGULAR">Regular</option>
                    <option value="POSTPONED">Postergación</option>
                    <option value="TEMPORARY_WITHDRAWAL">
                      Retiro temporal
                    </option>
                  </select>
                </Field>
              </FormPanel>
            )}
            {!data.registrations.length && !data.records.length && (
              <FormPanel
                title="Cambiar carrera / plan"
                submit={async (form) => {
                  await api.put(`/students/${id}/study-plan`, {
                    studyPlanId: number(form, "studyPlanId"),
                  });
                  resource.reload();
                }}
              >
                <Field label="Plan de destino">
                  <select
                    name="studyPlanId"
                    defaultValue={data.student.studyPlanId}
                    required
                  >
                    {data.plans
                      .filter((plan) =>
                        data.programs.some(
                          (program) =>
                            program.id === plan.degreeProgramId &&
                            program.status === "ACTIVE",
                        ),
                      )
                      .map((plan) => (
                        <option key={plan.id} value={plan.id}>
                          {
                            data.programs.find(
                              (program) => program.id === plan.degreeProgramId,
                            )?.name
                          }{" "}
                          · {plan.version}
                        </option>
                      ))}
                  </select>
                </Field>
              </FormPanel>
            )}
            <section className="panel">
              <h2>Inscripciones</h2>
              <Table
                headers={["Período", "Asignatura", "Sección", "Estado"]}
                empty={!data.registrations.length}
              >
                {data.registrations.map((registration) => (
                  <tr key={registration.id}>
                    <td>
                      {data.periods.find(
                        (period) => period.id === registration.academicPeriodId,
                      )
                        ? periodName(
                            data.periods.find(
                              (period) =>
                                period.id === registration.academicPeriodId,
                            )!,
                          )
                        : registration.academicPeriodId}
                    </td>
                    <td>{registration.courseName}</td>
                    <td>
                      <Link to={`/sections/${registration.sectionId}`}>
                        #{registration.sectionId}
                      </Link>
                    </td>
                    <td>
                      <Badge value={registration.status} />
                    </td>
                  </tr>
                ))}
              </Table>
            </section>
            <h2>Historial académico</h2>
            <History records={data.records} periods={data.periods} />
          </>
        )}
      </Resource>
    </>
  );
}
