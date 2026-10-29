package siga.ipt.backend.dtos;

public record UserRegistrationResponse(
        String keycloakSubject,
        String username,
        SelfRegistrationRole role,
        Long studentId,
        Long teacherId
) {
}
