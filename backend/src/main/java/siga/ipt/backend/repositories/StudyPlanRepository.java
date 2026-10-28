package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.StudyPlan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import siga.ipt.backend.entitys.enums.PlanStatus;

@Repository
public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {

    @Query("select p.degreeProgram.id from StudyPlan p where p.id = :id")
    Optional<Long> findDegreeProgramIdById(@Param("id") Long id);

    boolean existsByDegreeProgramId(Long degreeProgramId);

    List<StudyPlan> findByDegreeProgramId(Long degreeProgramId);

    List<StudyPlan> findByDegreeProgramIdAndStatus(
            Long degreeProgramId,
            PlanStatus status
    );

    boolean existsByDegreeProgramIdAndVersion(
            Long degreeProgramId,
            String version
    );

    boolean existsByDegreeProgramIdAndVersionAndIdNot(
            Long degreeProgramId,
            String version,
            Long id
    );
}
