package siga.ipt.backend.dtos;

import java.time.Instant;
import siga.ipt.backend.entitys.enums.PeriodStatus;

public record AcademicPeriodDto(
        Long id,
        Integer year,
        Integer semester,
        PeriodStatus status,
        Instant closedAt,
        Long closedById
) {
}
