package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.User;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKeycloakSubject(String subject);

    Optional<User> findByRun(String run);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByKeycloakSubject(String keycloakSubject);

}
