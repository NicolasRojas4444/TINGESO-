package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.DegreeProgram;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import siga.ipt.backend.entitys.enums.ActiveStatus;
import siga.ipt.backend.entitys.enums.PlanStatus;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface DegreeProgramRepository extends JpaRepository<DegreeProgram, Long> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("""
            select program from DegreeProgram program
            where program.status = :active
              and (select count(plan) from StudyPlan plan
                   where plan.degreeProgram = program and plan.status = :current) = 1
            order by program.name, program.code
            """)
    List<DegreeProgram> findRegistrationOptions(
            @Param("active") ActiveStatus active,
            @Param("current") PlanStatus current
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DegreeProgram d where d.id = :id")
    Optional<DegreeProgram> findByIdForUpdate(@Param("id") Long id);

}
