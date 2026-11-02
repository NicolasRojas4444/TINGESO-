import { studentService } from "~/service/studentService";
import { academicPeriodService } from "~/service/academicPeriodService";
import { useResource } from "~/hooks/useResource";
import { History } from "~/components/academic";
import { Notice, PageTitle, Resource } from "~/components/ui";

export default function HistoryView() {
  const resource = useResource(async (signal) => {
    const student = await studentService.getMe(signal);
    const [records, periods] = await Promise.all([
      studentService.getHistory(student.id, signal),
      academicPeriodService.getAll(signal),
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
