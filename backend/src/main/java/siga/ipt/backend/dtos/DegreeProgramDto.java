package siga.ipt.backend.dtos;

import siga.ipt.backend.entitys.enums.ActiveStatus;

public record DegreeProgramDto(
        Long id,
        String code,
        String name,
        String description,
        Integer durationSemesters,
        ActiveStatus status
) {
}
