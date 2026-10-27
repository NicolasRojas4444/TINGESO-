package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.Teacher;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {
}
