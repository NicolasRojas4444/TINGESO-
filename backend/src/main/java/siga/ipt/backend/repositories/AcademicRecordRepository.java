package siga.ipt.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import siga.ipt.backend.entitys.AcademicRecord;

@Repository
public interface AcademicRecordRepository extends JpaRepository<AcademicRecord, Long> {
}
