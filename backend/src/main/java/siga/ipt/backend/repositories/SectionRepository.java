package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.Section;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {

    @Query("select distinct s.course.studyPlan.degreeProgram.id from Section s "
            + "where s.academicPeriod.id = :periodId")
    List<Long> findDegreeProgramIdsByAcademicPeriodId(@Param("periodId") Long periodId);

    boolean existsByTeacherId(Long teacherId);

    boolean existsByTeacherIdAndAcademicPeriodStatus(
        Long teacherId,
        PeriodStatus status
    );


    boolean existsByCourseId(Long courseId);

    List<Section> findByCourseIdAndAcademicPeriodStatus(
        Long courseId,
        PeriodStatus status
    );

    boolean existsByCourseIdAndAcademicPeriodId(
        Long courseId,
        Long academicPeriodId
    );

    List<Section> findByAcademicPeriodId(Long academicPeriodId);

    List<Section> findByTeacherId(Long teacherId);

    List<Section> findByTeacherIdAndAcademicPeriodId(
        Long teacherId,
        Long academicPeriodId
    );

    @Query("select s.course.id from Section s where s.id = :id")
    Optional<Long> findCourseIdById(@Param("id") Long id);

    @Query("select s.academicPeriod.id from Section s where s.id = :id")
    Optional<Long> findAcademicPeriodIdById(@Param("id") Long id);
}
