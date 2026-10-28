package siga.ipt.backend.dtos;

import java.time.Instant;
import siga.ipt.backend.entitys.enums.RegistrationStatus;

public record CourseRegistrationDto(
        Long id,
        Long studentId,
        Long sectionId,
        Long courseId,
        Long academicPeriodId,
        String studentName,
        String courseName,
        RegistrationStatus status,
        Instant registeredAt,
        Instant cancelledAt
) {
}
