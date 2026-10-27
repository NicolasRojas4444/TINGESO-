package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.SectionScheduleBlock;

@Repository
public interface SectionScheduleBlockRepository extends JpaRepository<SectionScheduleBlock, Long> {
}
