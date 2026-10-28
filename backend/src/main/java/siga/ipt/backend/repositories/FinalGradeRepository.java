package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import siga.ipt.backend.entitys.enums.RegistrationStatus;
import siga.ipt.backend.entitys.FinalGrade;

@Repository
public interface FinalGradeRepository extends JpaRepository<FinalGrade, Long> {

    boolean existsByRegistrationId(Long registrationId);

    Optional<FinalGrade> findByRegistrationId(Long registrationId);

    List<FinalGrade> findByRegistrationSectionIdAndRegistrationStatus(
            Long sectionId, RegistrationStatus status);

    List<FinalGrade> findByRegistrationSectionAcademicPeriodIdAndRegistrationStatus(
            Long academicPeriodId, RegistrationStatus status);
}
