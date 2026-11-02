import type { Course } from "../../lib/types";
import { courseService } from "../../service/courseService";
import { number, text } from "../../utils/form";
import { FormPanel, TextField } from "../ui";

export function CourseForm({
  course,
  planId,
  saved,
  cancel,
}: {
  course?: Course;
  planId: number;
  saved: (course: Course) => void;
  cancel: () => void;
}) {
  return (
    <FormPanel
      title={course ? "Editar asignatura" : "Crear asignatura"}
      cancel={cancel}
      submit={async (form) => {
        const body = {
          studyPlanId: planId,
          code: text(form, "code"),
          name: text(form, "name"),
          semester: number(form, "semester"),
          theoryHours: number(form, "theoryHours"),
          exerciseHours: number(form, "exerciseHours"),
          laboratoryHours: number(form, "laboratoryHours"),
          sct: number(form, "sct"),
        };
        const result = course
          ? await courseService.update(course.id, body)
          : await courseService.create(body);
        saved(result);
      }}
    >
      <TextField
        name="code"
        label="Código"
        maxLength={30}
        value={course?.code}
      />
      <TextField
        name="name"
        label="Nombre"
        maxLength={200}
        value={course?.name}
      />
      <TextField
        name="semester"
        label="Semestre"
        type="number"
        min={1}
        max={4}
        value={course?.semester ?? 1}
      />
      <TextField
        name="theoryHours"
        label="Horas de teoría"
        type="number"
        min={0}
        max={7}
        value={course?.theoryHours ?? 2}
      />
      <TextField
        name="exerciseHours"
        label="Horas de ejercicios"
        type="number"
        min={0}
        max={7}
        value={course?.exerciseHours ?? 0}
      />
      <TextField
        name="laboratoryHours"
        label="Horas de laboratorio"
        type="number"
        min={0}
        max={7}
        value={course?.laboratoryHours ?? 0}
      />
      <TextField
        name="sct"
        label="Créditos SCT"
        type="number"
        min={1}
        max={7}
        value={course?.sct ?? 4}
      />
      <p className="hint">
        Los prerrequisitos se guardan por separado después de guardar la
        asignatura. Para secciones, cada bloque representa dos horas TEL.
      </p>
    </FormPanel>
  );
}
