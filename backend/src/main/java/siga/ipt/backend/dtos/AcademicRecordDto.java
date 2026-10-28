package siga.ipt.backend.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import siga.ipt.backend.entitys.enums.GradeResult;

public record AcademicRecordDto(
        Long id,
        Long registrationId,
        Long studentId,
        Long sectionId,
        Long courseId,
        Long academicPeriodId,
        String courseCode,
        String courseName,
        Integer curriculumSemester,
        Integer theoryHours,
        Integer exerciseHours,
        Integer laboratoryHours,
        Integer sct,
        BigDecimal finalGrade,
        GradeResult result,
        Instant consolidatedAt
) {
}
