package siga.ipt.backend.dtos;

public record UserProfileRecoveryResponse(
        Long userId,
        String keycloakSubject,
        SelfRegistrationRole role,
        Long studentId,
        Long teacherId
) {
}
