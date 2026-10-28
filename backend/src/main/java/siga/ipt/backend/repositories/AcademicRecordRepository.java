package siga.ipt.backend.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.AcademicRecord;
import siga.ipt.backend.entitys.enums.PeriodStatus;

@Repository
public interface AcademicRecordRepository extends JpaRepository<AcademicRecord, Long> {

    boolean existsByRegistrationId(Long registrationId);

    boolean existsByRegistrationStudentId(Long studentId);

    @Query("""
            select record
            from AcademicRecord record
            join record.registration registration
            join registration.section section
            join section.academicPeriod period
            where registration.student.id = :studentId
              and period.status = :periodStatus
            order by period.year desc,
                     period.semester desc,
                     record.courseCode asc
            """)
    List<AcademicRecord> findHistoryByStudentId(
            @Param("studentId") Long studentId,
            @Param("periodStatus") PeriodStatus periodStatus
    );

    boolean existsByRegistrationSectionCourseId(Long courseId);
}
