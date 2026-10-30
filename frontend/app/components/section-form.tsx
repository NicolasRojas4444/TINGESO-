import { useState } from "react";
import { api } from "../lib/api";
import type { Course, ScheduleBlock, Section, Teacher } from "../lib/types";
import { days, modules } from "./academic";
import { Field, FormPanel, TextField, number } from "./ui";

export function SectionForm({
  section,
  courses,
  teachers,
  periodId,
  saved,
  cancel,
}: {
  section?: Section;
  courses: Course[];
  teachers: Teacher[];
  periodId: number;
  saved: () => void;
  cancel: () => void;
}) {
  const [blocks, setBlocks] = useState<ScheduleBlock[]>(
    section?.scheduleBlocks.map(({ dayOfWeek, module }) => ({
      dayOfWeek,
      module,
    })) || [{ dayOfWeek: "MONDAY", module: 1 }],
  );
  return (
    <FormPanel
      title={section ? `Editar sección #${section.id}` : "Crear sección"}
      cancel={cancel}
      submit={async (form) => {
        const body = {
          courseId: section?.courseId || number(form, "courseId"),
          academicPeriodId: periodId,
          teacherId: number(form, "teacherId"),
          capacity: number(form, "capacity"),
          scheduleBlocks: blocks,
        };
        if (section) await api.put(`/sections/${section.id}`, body);
        else await api.post("/sections", body);
        saved();
      }}
    >
      {!section && (
        <Field label="Asignatura">
          <select name="courseId" defaultValue="" required>
            <option value="" disabled>
              Selecciona una asignatura
            </option>
            {courses.map((course) => (
              <option key={course.id} value={course.id}>
                {course.code} · {course.name} · Plan #{course.studyPlanId} · TEL{" "}
                {course.theoryHours +
                  course.exerciseHours +
                  course.laboratoryHours}
              </option>
            ))}
          </select>
        </Field>
      )}
      <Field label="Docente responsable">
        <select
          name="teacherId"
          defaultValue={section?.teacherId || ""}
          required
        >
          <option value="" disabled>
            Selecciona un docente activo
          </option>
          {teachers
            .filter((teacher) => teacher.status === "ACTIVE")
            .map((teacher) => (
              <option key={teacher.id} value={teacher.id}>
                {teacher.user.fullName}
              </option>
            ))}
        </select>
      </Field>
      <TextField
        name="capacity"
        label="Capacidad"
        type="number"
        min={1}
        max={60}
        value={section?.capacity || 30}
      />
      <div className="full-width">
        <h3>Bloques de horario</h3>
        <p className="hint">
          Cada bloque representa dos horas pedagógicas. La suma debe coincidir
          con el TEL de la asignatura.
        </p>
        {blocks.map((block, index) => (
          <div className="schedule-row" key={index}>
            <Field label={`Día del bloque ${index + 1}`}>
              <select
                value={block.dayOfWeek}
                onChange={(event) =>
                  setBlocks((previous) =>
                    previous.map((item, position) =>
                      position === index
                        ? { ...item, dayOfWeek: event.target.value }
                        : item,
                    ),
                  )
                }
              >
                {Object.entries(days).map(([key, label]) => (
                  <option key={key} value={key}>
                    {label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label={`Módulo del bloque ${index + 1}`}>
              <select
                value={block.module}
                onChange={(event) =>
                  setBlocks((previous) =>
                    previous.map((item, position) =>
                      position === index
                        ? { ...item, module: Number(event.target.value) }
                        : item,
                    ),
                  )
                }
              >
                {modules.map((hours, position) => (
                  <option key={position} value={position + 1}>
                    M{position + 1} · {hours}
                  </option>
                ))}
              </select>
            </Field>
            <button
              type="button"
              disabled={blocks.length === 1}
              onClick={() =>
                setBlocks((previous) =>
                  previous.filter((_, position) => position !== index),
                )
              }
            >
              Quitar
            </button>
          </div>
        ))}
        <button
          type="button"
          onClick={() =>
            setBlocks((previous) => [
              ...previous,
              { dayOfWeek: "MONDAY", module: 1 },
            ])
          }
        >
          Agregar bloque
        </button>
      </div>
    </FormPanel>
  );
}
