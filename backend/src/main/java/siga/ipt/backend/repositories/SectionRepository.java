package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.Section;

@Repository
public interface SectionRepository extends JpaRepository<Section, Long> {
}
