package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.User;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKeycloakSubject(String subject);

    Optional<User> findByRun(String run);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByKeycloakSubject(String keycloakSubject);

    List<User> findByAccessSuspendedTrue();

    List<User> findByAccessSuspendedFalse();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

}
