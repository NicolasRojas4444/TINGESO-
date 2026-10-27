package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.AcademicPeriod;

@Repository
public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, Long> {
}
