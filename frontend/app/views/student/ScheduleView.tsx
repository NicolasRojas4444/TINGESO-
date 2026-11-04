import { useState } from "react";
import { Link } from "react-router";
import { useResource } from "~/hooks/useResource";
import { studentService } from "~/service/studentService";
import { academicPeriodService } from "~/service/academicPeriodService";
import { registrationService } from "~/service/registrationService";
import { sectionService } from "~/service/sectionService";
import type { Registration } from "~/lib/types";
import { days, modules, periodName, scheduleLabel } from "~/components/academic";
import { Badge, Field, Notice, PageTitle, Resource, Table } from "~/components/ui";

function RegisteredSchedule({ registrations }: { registrations: Registration[] }) {
  const resource = useResource(async (signal) =>
    Promise.all([...new Set(registrations.map(item => item.sectionId))]
      .map(id => sectionService.getById(id, signal))),
    [registrations.map(item => item.sectionId).join(",")],
  );
  const sections = resource.data;
  return (
    <Resource {...resource} retry={resource.reload}>
      {sections && (sections.length ? (
        <>
          <section className="panel">
            <h2>Mis asignaturas inscritas</h2>
            <Table headers={["Asignatura", "Docente", "Horario"]}>
              {sections.map(section => (
                <tr key={section.id}>
                  <td>{section.courseName}</td>
                  <td>{section.teacherName}</td>
                  <td>{scheduleLabel(section.scheduleBlocks) || "Sin horario asignado"}</td>
                </tr>
              ))}
            </Table>
          </section>
          <section className="panel">
            <h2>Horario semanal</h2>
            <p className="hint">Solo se muestran tus inscripciones vigentes del período seleccionado.</p>
            <div className="table-wrap">
              <table className="weekly-schedule">
                <caption>Horario semanal de mis asignaturas inscritas</caption>
                <thead><tr>
                  <th scope="col">Módulo</th>
                  {Object.entries(days).map(([day, label]) => <th scope="col" key={day}>{label}</th>)}
                </tr></thead>
                <tbody>{modules.map((time, index) => (
                  <tr key={time}>
                    <th scope="row">M{index + 1}<span className="schedule-time">{time}</span></th>
                    {Object.keys(days).map(day => (
                      <td key={day}>
                        {sections.filter(section => section.scheduleBlocks.some(block =>
                          block.dayOfWeek === day && block.module === index + 1,
                        )).map(section => (
                          <div className="schedule-course" key={section.id}>
                            <strong>{section.courseName}</strong>
                            <span>{section.teacherName}</span>
                          </div>
                        ))}
                      </td>
                    ))}
                  </tr>
                ))}</tbody>
              </table>
            </div>
          </section>
        </>
      ) : <Notice kind="info">No tienes asignaturas inscritas en este período. Puedes elegir tus ramos desde Inscripción académica.</Notice>)}
    </Resource>
  );
}

export default function ScheduleView() {
  const [periodId, setPeriodId] = useState("");
  const resource = useResource(async (signal) => {
    const student = await studentService.getMe(signal);
    const [periods, registrations] = await Promise.all([
      academicPeriodService.getAll(signal),
      registrationService.getByStudent(student.id, signal),
    ]);
    const active = registrations.filter(item => item.status === "ACTIVE");
    return {
      active,
      periods: periods.filter(period => period.status === "OPEN" || active.some(item => item.academicPeriodId === period.id))
        .sort((a, b) => b.year - a.year || b.semester - a.semester),
    };
  });
  const data = resource.data;
  const selected = data?.periods.find(period => period.id === Number(periodId)) || data?.periods[0];
  return (
    <>
      <PageTitle title="Mis ramos y horario" />
      <p>Consulta tus asignaturas inscritas y su distribución semanal. La malla completa está en <Link to="/student/plan">Mi plan de estudios</Link>.</p>
      <Resource {...resource} retry={resource.reload}>
        {data && (
          <>
            <section className="panel">
              <Field label="Período académico">
                <select value={selected?.id || ""} onChange={event => setPeriodId(event.target.value)}>
                  {!data.periods.length && <option value="">Sin períodos disponibles</option>}
                  {data.periods.map(period => <option value={period.id} key={period.id}>{periodName(period)}</option>)}
                </select>
              </Field>
              {selected && <Badge value={selected.status} />}
            </section>
            {selected ? <RegisteredSchedule key={selected.id} registrations={data.active.filter(item => item.academicPeriodId === selected.id)} />
              : <Notice kind="info">Aún no hay períodos disponibles para consultar tu horario.</Notice>}
          </>
        )}
      </Resource>
    </>
  );
}
