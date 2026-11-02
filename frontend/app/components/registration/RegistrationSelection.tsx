import { useState } from "react";
import { useResource } from "../../hooks/useResource";
import type { Period, Student } from "../../lib/types";
import { registrationService } from "../../service/registrationService";
import { sectionService } from "../../service/sectionService";
import { Notice, Resource } from "../ui";
import { SelectionEditor } from "./SelectionEditor";

export function RegistrationSelection({
  student,
  period,
}: {
  student: Student;
  period: Period;
}) {
  const [success, setSuccess] = useState("");
  const resource = useResource(
    async (signal) => {
      const [options, registrations, sections] = await Promise.all([
        registrationService.getReplacementOptions(
          student.id,
          period.id,
          signal,
        ),
        registrationService.getByStudent(student.id, signal),
        sectionService.getByPeriod(period.id, signal),
      ]);
      const active = registrations.filter(
        (registration) =>
          registration.academicPeriodId === period.id &&
          registration.status === "ACTIVE",
      );
      // Retain existing selections even if later catalog changes affect eligibility.
      const merged = [...options];
      active.forEach((registration) => {
        if (!merged.some((section) => section.id === registration.sectionId)) {
          const section = sections.find(
            (item) => item.id === registration.sectionId,
          );
          if (section) merged.push(section);
        }
      });
      return { options: merged, active };
    },
    [student.id, period.id],
  );
  return (
    <>
      <Notice kind="success">{success}</Notice>
      <Resource {...resource} retry={resource.reload}>
        {resource.data && (
          <SelectionEditor
            key={
              resource.data.active.map((item) => item.id).join(",") +
              resource.data.options
                .map((item) => `${item.id}:${item.availableSeats}`)
                .join(",")
            }
            student={student}
            period={period}
            options={resource.data.options}
            active={resource.data.active}
            reload={resource.reload}
            saved={() => {
              setSuccess("Selección guardada correctamente.");
              resource.reload();
            }}
          />
        )}
      </Resource>
    </>
  );
}
