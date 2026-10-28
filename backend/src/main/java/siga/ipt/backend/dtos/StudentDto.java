package siga.ipt.backend.dtos;

import siga.ipt.backend.entitys.enums.StudentStatus;

public record StudentDto(
        Long id,
        UserDto user,
        Long studyPlanId,
        Long degreeProgramId,
        StudentStatus academicStatus
) {
}
