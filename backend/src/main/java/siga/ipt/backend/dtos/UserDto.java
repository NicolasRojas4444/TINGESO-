package siga.ipt.backend.dtos;

public record UserDto(
        Long id,
        String keycloakSubject,
        String run,
        String fullName,
        String email
) {
}
