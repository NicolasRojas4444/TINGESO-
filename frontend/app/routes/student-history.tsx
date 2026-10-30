import { get } from "../lib/api";
import { useResource } from "../lib/hooks";
import type { AcademicRecord, Period, Student } from "../lib/types";
import { History } from "../components/academic";
import { Notice, PageTitle, Resource } from "../components/ui";

export default function StudentHistory() {
  const resource = useResource(async (signal) => {
    const student = await get<Student>("/students/me", signal);
    const [records, periods] = await Promise.all([
      get<AcademicRecord[]>(`/students/${student.id}/history`, signal),
      get<Period[]>("/academic-periods", signal),
    ]);
    return { records, periods };
  });
  return (
    <>
      <PageTitle title="Historial académico" />
      <Notice kind="info">
        Solo se muestran notas y resultados consolidados en períodos cerrados.
      </Notice>
      <Resource {...resource} retry={resource.reload}>
        {resource.data && <History {...resource.data} />}
      </Resource>
    </>
  );
}
