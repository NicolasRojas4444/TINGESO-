package siga.ipt.backend.services;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import siga.ipt.backend.dtos.AcademicRecordDto;
import siga.ipt.backend.mappers.AcademicMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.entitys.AcademicRecord;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.repositories.AcademicRecordRepository;
import siga.ipt.backend.repositories.StudentRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicHistoryService {

    private final AcademicMapper mapper;
    private final CurrentUserService currentUser;

    private final AcademicRecordRepository academicRecordRepository;
    private final StudentRepository studentRepository;

    private List<AcademicRecord> getByStudentEntity(Long studentId) {
        if (studentId == null) {
            throw new IllegalArgumentException(
                    "The student ID is required"
            );
        }

        if (!studentRepository.existsById(studentId)) {
            throw new EntityNotFoundException(
                    "Student not found with ID: " + studentId
            );
        }

        return academicRecordRepository.findHistoryByStudentId(
                studentId,
                PeriodStatus.CLOSED
        );
    }

    public List<AcademicRecordDto> getByStudent(Long studentId) {
        currentUser.requireStudentAccess(studentId);
        return getByStudentEntity(studentId).stream().map(mapper::toDto).toList();
    }
}
