package siga.ipt.backend.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserRegistrationRequest(
        String username,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) String password,
        String run,
        String fullName,
        String email,
        SelfRegistrationRole role,
        Long degreeProgramId,
        String professionalTitle,
        String academicDegree
) {
    @Override
    public String toString() {
        return "UserRegistrationRequest[REDACTED]";
    }
}
