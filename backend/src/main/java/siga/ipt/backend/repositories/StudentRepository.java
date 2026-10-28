package siga.ipt.backend.repositories;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.Student;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    boolean existsByUserId(Long userId);

    Optional<Student> findByUserKeycloakSubject(String subject);

    @Query("select s.id from Student s where s.user.keycloakSubject = :subject")
    Optional<Long> findIdByUserSubject(@Param("subject") String subject);

    @Query("select s.studyPlan.degreeProgram.id from Student s where s.id = :id")
    Optional<Long> findDegreeProgramIdById(@Param("id") Long id);

    boolean existsByStudyPlanId(Long studyPlanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Student s where s.id = :id")
    Optional<Student> findByIdForUpdate(@Param("id") Long id);
}
