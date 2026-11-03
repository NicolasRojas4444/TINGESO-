import { Link, useParams } from "react-router";
import { useResource } from "~/hooks/useResource";
import { studentService } from "~/service/studentService";
import { registrationService } from "~/service/registrationService";
import { programService } from "~/service/programService";
import { studyPlanService } from "~/service/studyPlanService";
import { academicPeriodService } from "~/service/academicPeriodService";
import { History, periodName } from "~/components/academic";
import { number, text } from "~/utils/form";
import {
  Badge,
  Field,
  FormPanel,
  PageTitle,
  Resource,
  Table,
} from "~/components/ui";

export default function StudentDetailView() {
  const id = Number(useParams().id);
  const resource = useResource(
    async (signal) => {
      const [student, records, registrations, programs, plans, periods] =
        await Promise.all([
          studentService.getById(id, signal),
          studentService.getHistory(id, signal),
          registrationService.getByStudent(id, signal),
          programService.getAll(signal),
          studyPlanService.getAll(signal),
          academicPeriodService.getAll(signal),
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
              </dl>
            </section>
            {!["GRADUATED", "ELIMINATED"].includes(
              data.student.academicStatus,
            ) && (
              <FormPanel
                title="Cambiar estado académico"
                submit={async (form) => {
                  await studentService.changeStatus(id, text(form, "status"));
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
                  await studentService.changePlan(
                    id,
                    number(form, "studyPlanId"),
                  );
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
