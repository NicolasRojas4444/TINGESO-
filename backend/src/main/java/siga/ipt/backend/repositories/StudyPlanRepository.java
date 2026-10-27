package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.StudyPlan;

@Repository
public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {
}
