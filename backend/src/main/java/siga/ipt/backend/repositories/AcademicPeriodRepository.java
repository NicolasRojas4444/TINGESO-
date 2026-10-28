package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.AcademicPeriod;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface AcademicPeriodRepository extends JpaRepository<AcademicPeriod, Long> {
    boolean existsByClosedById(Long userId);

    boolean existsByYearAndSemester(Integer year, Integer semester);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from AcademicPeriod p where p.id = :id")
    Optional<AcademicPeriod> findByIdForUpdate(@Param("id") Long id);
}
