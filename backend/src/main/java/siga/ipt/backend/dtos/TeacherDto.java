package siga.ipt.backend.dtos;

import siga.ipt.backend.entitys.enums.ActiveStatus;

public record TeacherDto(
        Long id,
        UserDto user,
        String professionalTitle,
        String academicDegree,
        ActiveStatus status
) {
}
