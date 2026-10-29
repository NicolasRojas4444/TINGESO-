package siga.ipt.backend.dtos;

import java.util.List;

public record SectionDto(
        Long id,
        Long courseId,
        Long academicPeriodId,
        Long teacherId,
        String courseName,
        String teacherName,
        Integer capacity,
        Long registeredCount,
        Long availableSeats,
        List<SectionScheduleBlockDto> scheduleBlocks
) {
}
