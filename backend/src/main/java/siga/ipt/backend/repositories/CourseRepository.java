package siga.ipt.backend.repositories;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.Course;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    boolean existsByStudyPlanId(Long studyPlanId);

    List<Course> findByStudyPlanIdOrderBySemesterAscCodeAsc(Long studyPlanId);

    Optional<Course> findByStudyPlanIdAndCode(
            Long studyPlanId,
            String code
    );

    @Query("""
            select c.studyPlan.degreeProgram.id
            from Course c
            where c.id = :id
            """)
    Optional<Long> findDegreeProgramIdById(@Param("id") Long id);
}
