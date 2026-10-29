package siga.ipt.backend.dtos;

public record UserProfileRecoveryRequest(
        SelfRegistrationRole role,
        Long degreeProgramId,
        String professionalTitle,
        String academicDegree
) {
}
