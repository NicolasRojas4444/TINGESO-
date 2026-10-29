package siga.ipt.backend.dtos;

/** Credential-free snapshot used only for best-effort compensation. */
public record KeycloakAccountState(
        boolean enabled,
        String email,
        String firstName,
        String lastName,
        boolean emailVerified
) {
    public KeycloakAccountState withEnabled(boolean value) {
        return new KeycloakAccountState(value, email, firstName, lastName, emailVerified);
    }
}
