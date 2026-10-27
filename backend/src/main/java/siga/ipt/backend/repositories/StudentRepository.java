package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.Student;

@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
}
