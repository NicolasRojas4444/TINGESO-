package siga.ipt.backend.exceptions;

public class IdentityProviderUnavailableException extends RuntimeException {

    public IdentityProviderUnavailableException() {
        super("The identity provider could not complete the operation; try again later");
    }
}
