package siga.ipt.backend.dtos;

import java.time.DayOfWeek;

public record SectionScheduleBlockDto(
        Long id,
        DayOfWeek dayOfWeek,
        Integer module
) {
}
