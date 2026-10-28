package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.CourseRegistration;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import siga.ipt.backend.entitys.Section;
import siga.ipt.backend.entitys.enums.RegistrationStatus;

@Repository
public interface CourseRegistrationRepository extends JpaRepository<CourseRegistration, Long> {

    List<CourseRegistration> findByStudentIdOrderByRegisteredAtDesc(Long studentId);

    List<CourseRegistration> findByStudentIdAndSectionAcademicPeriodIdAndStatus(
            Long studentId, Long academicPeriodId, RegistrationStatus status);

    List<CourseRegistration> findBySectionIdAndStatus(Long sectionId, RegistrationStatus status);

    List<CourseRegistration> findBySectionAcademicPeriodIdAndStatus(
            Long academicPeriodId, RegistrationStatus status);

    Optional<CourseRegistration> findByStudentIdAndSectionId(Long studentId, Long sectionId);

    @Query("select r.section.academicPeriod.id from CourseRegistration r where r.id = :id")
    Optional<Long> findAcademicPeriodIdById(@Param("id") Long id);

    @Query("select r.student.id from CourseRegistration r where r.id = :id")
    Optional<Long> findStudentIdById(@Param("id") Long id);

    boolean existsByStudentId(Long studentId);

    boolean existsBySectionId(Long sectionId);

    long countBySectionIdAndStatus(
        Long sectionId,
        RegistrationStatus status
    );

    @Query("""
        select distinct otherSection
        from CourseRegistration registration
        join registration.section otherSection
        left join fetch otherSection.scheduleBlocks
        where registration.status = :status
          and otherSection.academicPeriod.id = :periodId
          and otherSection.id <> :sectionId
          and registration.student.id in (
              select enrolled.student.id
              from CourseRegistration enrolled
              where enrolled.section.id = :sectionId
                and enrolled.status = :status
          )
        """)
    List<Section> findOtherSectionsForEnrolledStudents(
        @Param("sectionId") Long sectionId,
        @Param("periodId") Long periodId,
        @Param("status") RegistrationStatus status
    );

}
