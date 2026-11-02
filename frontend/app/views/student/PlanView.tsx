import { studentService } from "~/service/studentService";
import { courseService } from "~/service/courseService";
import { useResource } from "~/hooks/useResource";
import { Curriculum } from "~/components/academic";
import { PageTitle, Resource } from "~/components/ui";

export default function PlanView() {
  const resource = useResource(async (signal) => {
    const student = await studentService.getMe(signal);
    return courseService.getCurriculum(student.studyPlanId, signal);
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
