import { useState } from "react";
import { Link, useParams } from "react-router";
import { api, get } from "../lib/api";
import { useResource } from "../lib/hooks";
import { loadCurriculum } from "../lib/curriculum";
import type { Course, Plan, Program } from "../lib/types";
import {
  ActionButton,
  Badge,
  Field,
  FormPanel,
  Notice,
  PageTitle,
  Resource,
  Table,
  TextField,
  number,
  text,
} from "../components/ui";

export default function PlanDetail() {
  const { id } = useParams();
  const resource = useResource(
    async (signal) => {
      const plan = await get<Plan>(`/study-plans/${id}`, signal);
      const [program, curriculum] = await Promise.all([
        get<Program>(`/degree-programs/${plan.degreeProgramId}`, signal),
        loadCurriculum(plan.id, signal),
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
              <FormPanel
                key={editing === "new" ? "new" : editing.id}
                title={
                  editing === "new" ? "Crear asignatura" : "Editar asignatura"
                }
                cancel={() => setEditing(null)}
                submit={async (form) => {
                  const body = {
                    studyPlanId: Number(id),
                    code: text(form, "code"),
                    name: text(form, "name"),
                    semester: number(form, "semester"),
                    theoryHours: number(form, "theoryHours"),
                    exerciseHours: number(form, "exerciseHours"),
                    laboratoryHours: number(form, "laboratoryHours"),
                    sct: number(form, "sct"),
                  };
                  const result =
                    editing === "new"
                      ? await api.post<Course>("/courses", body)
                      : await api.put<Course>(`/courses/${editing.id}`, body);
                  setEditing(null);
                  setPrerequisiteCourse(result.data);
                  resource.reload();
                }}
              >
                <TextField
                  name="code"
                  label="Código"
                  maxLength={30}
                  value={editing === "new" ? "" : editing.code}
                />
                <TextField
                  name="name"
                  label="Nombre"
                  maxLength={200}
                  value={editing === "new" ? "" : editing.name}
                />
                <TextField
                  name="semester"
                  label="Semestre"
                  type="number"
                  min={1}
                  max={4}
                  value={editing === "new" ? 1 : editing.semester}
                />
                <TextField
                  name="theoryHours"
                  label="Horas de teoría"
                  type="number"
                  min={0}
                  max={7}
                  value={editing === "new" ? 2 : editing.theoryHours}
                />
                <TextField
                  name="exerciseHours"
                  label="Horas de ejercicios"
                  type="number"
                  min={0}
                  max={7}
                  value={editing === "new" ? 0 : editing.exerciseHours}
                />
                <TextField
                  name="laboratoryHours"
                  label="Horas de laboratorio"
                  type="number"
                  min={0}
                  max={7}
                  value={editing === "new" ? 0 : editing.laboratoryHours}
                />
                <TextField
                  name="sct"
                  label="Créditos SCT"
                  type="number"
                  min={1}
                  max={7}
                  value={editing === "new" ? 4 : editing.sct}
                />
                <p className="hint">
                  Los prerrequisitos se guardan por separado después de guardar
                  la asignatura. Para secciones, cada bloque representa dos
                  horas TEL.
                </p>
              </FormPanel>
            )}
            {prerequisiteCourse && (
              <FormPanel
                key={`prerequisites-${prerequisiteCourse.id}`}
                title={`Prerrequisitos · ${prerequisiteCourse.code}`}
                cancel={() => setPrerequisiteCourse(null)}
                submit={async (form) => {
                  const selected = form.getAll("prerequisiteIds").map(Number);
                  if (selected.length > 3)
                    throw new Error(
                      "Selecciona como máximo tres prerrequisitos.",
                    );
                  await api.put(
                    `/courses/${prerequisiteCourse.id}/prerequisites`,
                    selected,
                  );
                  setPrerequisiteCourse(null);
                  resource.reload();
                }}
              >
                <div className="checkbox-list">
                  <p>
                    Selecciona hasta tres asignaturas de semestres anteriores.
                    Deja vacío si no tiene prerrequisitos.
                  </p>
                  {data.courses
                    .filter(
                      (course) => course.semester < prerequisiteCourse.semester,
                    )
                    .map((course) => (
                      <label className="check" key={course.id}>
                        <input
                          type="checkbox"
                          name="prerequisiteIds"
                          value={course.id}
                          defaultChecked={data.prerequisites[
                            prerequisiteCourse.id
                          ]?.some((item) => item.id === course.id)}
                        />
                        {course.code} · {course.name}
                      </label>
                    ))}
                </div>
              </FormPanel>
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
                              run={() => api.delete(`/courses/${course.id}`)}
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
