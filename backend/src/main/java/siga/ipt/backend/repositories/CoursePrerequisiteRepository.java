package siga.ipt.backend.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.CoursePrerequisite;

@Repository
public interface CoursePrerequisiteRepository extends JpaRepository<CoursePrerequisite, Long> {

    List<CoursePrerequisite> findByCourseId(Long courseId);

    List<CoursePrerequisite> findByPrerequisiteId(Long prerequisiteId);
}
