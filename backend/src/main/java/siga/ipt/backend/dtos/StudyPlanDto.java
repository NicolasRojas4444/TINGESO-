package siga.ipt.backend.dtos;

import siga.ipt.backend.entitys.enums.PlanStatus;

public record StudyPlanDto(
        Long id,
        Long degreeProgramId,
        String version,
        PlanStatus status
) {
}
