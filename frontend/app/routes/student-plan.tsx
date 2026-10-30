import { get } from "../lib/api";
import { useResource } from "../lib/hooks";
import type { Student } from "../lib/types";
import { loadCurriculum } from "../lib/curriculum";
import { Curriculum } from "../components/academic";
import { PageTitle, Resource } from "../components/ui";

export default function StudentPlan() {
  const resource = useResource(async (signal) => {
    const student = await get<Student>("/students/me", signal);
    return loadCurriculum(student.studyPlanId, signal);
  });
  return (
    <>
      <PageTitle title="Mi plan de estudios" />
      <Resource {...resource} retry={resource.reload}>
        {resource.data && <Curriculum {...resource.data} />}
      </Resource>
    </>
  );
}
