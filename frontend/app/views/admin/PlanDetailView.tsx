import { useState } from "react";
import { Link, useParams } from "react-router";
import { useResource } from "~/hooks/useResource";
import type { Course } from "~/lib/types";
import { courseService } from "~/service/courseService";
import { programService } from "~/service/programService";
import { studyPlanService } from "~/service/studyPlanService";
import { CourseForm } from "~/components/forms/CourseForm";
import { PrerequisiteForm } from "~/components/forms/PrerequisiteForm";
import {
  ActionButton,
  Badge,
  Notice,
  PageTitle,
  Resource,
  Table,
} from "~/components/ui";

export default function PlanDetailView() {
  const id = Number(useParams().id);
  const resource = useResource(
    async (signal) => {
      const plan = await studyPlanService.getById(id, signal);
      const [program, curriculum] = await Promise.all([
        programService.getById(plan.degreeProgramId, signal),
        courseService.getCurriculum(plan.id, signal),
      ]);
      return { plan, program, ...curriculum };
    },
    [id],
  );
  const [editing, setEditing] = useState<Course | "new" | null>(null);
  const [prerequisiteCourse, setPrerequisiteCourse] = useState<Course | null>(
    null,
  );
  const data = resource.data;
  return (
    <>
      {data && (
        <Link to={`/admin/programs/${data.program.id}`}>
          ← Planes de {data.program.name}
        </Link>
      )}
      <PageTitle
        title={
          data
            ? `${data.program.name} · Plan ${data.plan.version}`
            : "Asignaturas"
        }
      >
        <button
          className="primary"
          onClick={() => {
            setPrerequisiteCourse(null);
            setEditing("new");
          }}
        >
          Nueva asignatura
        </button>
      </PageTitle>
      <Resource {...resource} retry={resource.reload}>
        {data && (
          <>
            <Badge value={data.plan.status} />
            {editing && (
              <CourseForm
                key={editing === "new" ? "new" : editing.id}
                course={editing === "new" ? undefined : editing}
                planId={id}
                cancel={() => setEditing(null)}
                saved={(course) => {
                  setEditing(null);
                  setPrerequisiteCourse(course);
                  resource.reload();
                }}
              />
            )}
            {prerequisiteCourse && (
              <PrerequisiteForm
                key={prerequisiteCourse.id}
                course={prerequisiteCourse}
                courses={data.courses}
                selected={data.prerequisites[prerequisiteCourse.id] || []}
                cancel={() => setPrerequisiteCourse(null)}
                saved={() => {
                  setPrerequisiteCourse(null);
                  resource.reload();
                }}
              />
            )}
            <Notice kind="info">
              Guardar datos de una asignatura no modifica las notas y datos ya
              consolidados en el historial.
            </Notice>
            {[1, 2, 3, 4].map((semester) => {
              const rows = data.courses.filter(
                (course) => course.semester === semester,
              );
              return (
                <section className="panel" key={semester}>
                  <h2>Semestre {semester}</h2>
                  <Table
                    headers={[
                      "Código",
                      "Asignatura",
                      "TEL",
                      "SCT",
                      "Prerrequisitos",
                      "Acciones",
                    ]}
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
                          {data.prerequisites[course.id]
                            ?.map((item) => item.code)
                            .join(", ") || "—"}
                        </td>
                        <td>
                          <div className="actions">
                            <button
                              onClick={() => {
                                setPrerequisiteCourse(null);
                                setEditing(course);
                              }}
                            >
                              Editar
                            </button>
                            <button
                              onClick={() => {
                                setEditing(null);
                                setPrerequisiteCourse(course);
                              }}
                            >
                              Prerrequisitos
                            </button>
                            <ActionButton
                              danger
                              confirm={`¿Eliminar ${course.code}?`}
                              run={() => courseService.remove(course.id)}
                              onSuccess={() => {
                                setEditing(null);
                                setPrerequisiteCourse(null);
                                resource.reload();
                              }}
                            >
                              Eliminar
                            </ActionButton>
                          </div>
                        </td>
                      </tr>
                    ))}
                  </Table>
                </section>
              );
            })}
          </>
        )}
      </Resource>
    </>
  );
}
