import type { Course } from "../../lib/types";
import { courseService } from "../../service/courseService";
import { FormPanel } from "../ui";

export function PrerequisiteForm({
  course,
  courses,
  selected,
  saved,
  cancel,
}: {
  course: Course;
  courses: Course[];
  selected: Course[];
  saved: () => void;
  cancel: () => void;
}) {
  return (
    <FormPanel
      title={`Prerrequisitos · ${course.code}`}
      cancel={cancel}
      submit={async (form) => {
        const ids = form.getAll("prerequisiteIds").map(Number);
        if (ids.length > 3)
          throw new Error("Selecciona como máximo tres prerrequisitos.");
        await courseService.setPrerequisites(course.id, ids);
        saved();
      }}
    >
      <div className="checkbox-list">
        <p>
          Selecciona hasta tres asignaturas de semestres anteriores. Deja vacío
          si no tiene prerrequisitos.
        </p>
        {courses
          .filter((item) => item.semester < course.semester)
          .map((item) => (
            <label className="check" key={item.id}>
              <input
                type="checkbox"
                name="prerequisiteIds"
                value={item.id}
                defaultChecked={selected.some(
                  (prerequisite) => prerequisite.id === item.id,
                )}
              />
              {item.code} · {item.name}
            </label>
          ))}
      </div>
    </FormPanel>
  );
}
