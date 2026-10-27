package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
