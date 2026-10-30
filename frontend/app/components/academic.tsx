import type {
  Course,
  AcademicRecord,
  Period,
  ScheduleBlock,
} from "../lib/types";
import { Badge, Table } from "./ui";

export const days: Record<string, string> = {
  MONDAY: "Lunes",
  TUESDAY: "Martes",
  WEDNESDAY: "Miércoles",
  THURSDAY: "Jueves",
  FRIDAY: "Viernes",
};
export const modules = [
  "08:15–09:35",
  "09:50–11:10",
  "11:25–12:45",
  "13:45–15:05",
  "15:20–16:40",
  "16:55–18:15",
];
export const periodName = (period: Period) =>
  `${period.year}-${period.semester}`;
export const scheduleLabel = (blocks: ScheduleBlock[]) =>
  blocks
    .map(
      (block) =>
        `${days[block.dayOfWeek]} M${block.module} (${modules[block.module - 1]})`,
    )
    .join(" · ");
export function Curriculum({
  courses,
  prerequisites,
}: {
  courses: Course[];
  prerequisites: Record<number, Course[]>;
}) {
  return (
    <>
      {[1, 2, 3, 4].map((semester) => {
        const rows = courses.filter((course) => course.semester === semester);
        return (
          <section key={semester} className="panel">
            <h2>Semestre {semester}</h2>
            <Table
              headers={["Código", "Asignatura", "TEL", "SCT", "Prerrequisitos"]}
              empty={!rows.length}
            >
              {rows.map((course) => (
                <tr key={course.id}>
                  <td>{course.code}</td>
                  <td>{course.name}</td>
                  <td>
                    {course.theoryHours}-{course.exerciseHours}-
                    {course.laboratoryHours}
                  </td>
                  <td>{course.sct}</td>
                  <td>
                    {prerequisites[course.id]
                      ?.map((item) => item.code)
                      .join(", ") || "—"}
                  </td>
                </tr>
              ))}
            </Table>
          </section>
        );
      })}
    </>
  );
}
export function History({
  records,
  periods,
}: {
  records: AcademicRecord[];
  periods: Period[];
}) {
  const groups = [...new Set(records.map((record) => record.academicPeriodId))];
  return groups.length ? (
    <>
      {groups.map((id) => {
        const period = periods.find((item) => item.id === id);
        return (
          <section className="panel" key={id}>
            <h2>Período {period ? periodName(period) : `#${id}`}</h2>
            <Table
              headers={["Código", "Asignatura", "Nota final", "Resultado"]}
            >
              {records
                .filter((record) => record.academicPeriodId === id)
                .map((record) => (
                  <tr key={record.id}>
                    <td>{record.courseCode}</td>
                    <td>{record.courseName}</td>
                    <td>{record.finalGrade.toFixed(1)}</td>
                    <td>
                      <Badge value={record.result} />
                    </td>
                  </tr>
                ))}
            </Table>
          </section>
        );
      })}
    </>
  ) : (
    <p className="panel">Aún no hay resultados de períodos cerrados.</p>
  );
}
