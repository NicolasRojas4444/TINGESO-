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
        List<SectionScheduleBlockDto> scheduleBlocks
) {
}
