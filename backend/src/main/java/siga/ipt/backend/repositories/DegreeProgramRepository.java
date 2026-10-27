package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.DegreeProgram;

@Repository
public interface DegreeProgramRepository extends JpaRepository<DegreeProgram, Long> {
}
