package siga.ipt.backend.services;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import siga.ipt.backend.dtos.UserRegistrationRequest;
import siga.ipt.backend.dtos.KeycloakAccountState;
import siga.ipt.backend.dtos.SelfRegistrationRole;
import siga.ipt.backend.exceptions.IdentityProviderUnavailableException;

@Service
public class KeycloakRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakRegistrationService.class);

    private final RestClient client;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakRegistrationService(
            @Value("${app.keycloak.server-url}") String serverUrl,
            @Value("${app.keycloak.realm}") String realm,
            @Value("${app.keycloak.registration-client-id}") String clientId,
            @Value("${app.keycloak.registration-client-secret}") String clientSecret,
            @Value("${app.keycloak.request-timeout-seconds}") long timeoutSeconds
    ) {
        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(serverUrl).requestFactory(factory).build();
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String createAccount(UserRegistrationRequest request) {
        String subject = null;

        try {
            String token = accessToken();
            String name = request.fullName().trim();
            int separator = name.lastIndexOf(' ');
            Map<String, Object> account = Map.of(
                    "username", request.username().trim(),
                    "email", request.email().trim().toLowerCase(Locale.ROOT),
                    "firstName", separator > 0 ? name.substring(0, separator) : name,
                    "lastName", separator > 0 ? name.substring(separator + 1) : name,
                    "enabled", false,
                    "emailVerified", false,
                    "credentials", List.of(Map.of(
                            "type", "password",
                            "value", request.password(),
                            "temporary", false
                    ))
            );

            ResponseEntity<Void> response = client.post()
                    .uri("/admin/realms/{realm}/users", realm)
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(account)
                    .retrieve()
                    .toBodilessEntity();

            URI location = response.getHeaders().getLocation();
            if (location == null) {
                throw new IdentityProviderUnavailableException();
            }
            subject = location.getPath().substring(location.getPath().lastIndexOf('/') + 1);

            Map<String, Object> role = client.get()
                    .uri("/admin/realms/{realm}/roles/{role}", realm, request.role().name())
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (role == null) {
                throw new IdentityProviderUnavailableException();
            }

            client.post()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm", realm, subject)
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(role))
                    .retrieve()
                    .toBodilessEntity();

            return subject;
        } catch (RuntimeException exception) {
            if (subject != null) {
                rollbackAccount(subject);
            }
            if (exception instanceof RestClientResponseException response) {
                if (response.getStatusCode().value() == 409) {
                    throw new IllegalStateException("The username or email is already registered");
                }
                if (response.getStatusCode().value() == 400) {
                    throw new IllegalArgumentException("Keycloak rejected the account data or password policy");
                }
            }
            // Do not propagate upstream bodies, credentials or tokens to API responses/logs.
            throw new IdentityProviderUnavailableException();
        }
    }

    public void enableAccount(String subject) {
        try {
            String token = accessToken();
            client.put()
                    .uri("/admin/realms/{realm}/users/{id}", realm, subject)
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("enabled", true))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new IdentityProviderUnavailableException();
        }
    }

    public void rollbackAccount(String subject) {
        try {
            String token = accessToken();
            client.delete()
                    .uri("/admin/realms/{realm}/users/{id}", realm, subject)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException exception) {
            // Distributed compensation can fail if Keycloak is unavailable. Keep the ID for recovery.
            log.error("Could not remove incomplete Keycloak account {}; manual cleanup is required", subject);
        }
    }

    public KeycloakAccountState getAccountState(String subject) {
        try {
            String token = accessToken();
            Map<String, Object> account = client.get()
                    .uri("/admin/realms/{realm}/users/{id}", realm, subject)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            if (account == null) {
                throw new IdentityProviderUnavailableException();
            }
            return new KeycloakAccountState(Boolean.TRUE.equals(account.get("enabled")),
                    java.util.Objects.toString(account.get("email"), ""),
                    java.util.Objects.toString(account.get("firstName"), ""),
                    java.util.Objects.toString(account.get("lastName"), ""),
                    Boolean.TRUE.equals(account.get("emailVerified")));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new EntityNotFoundException("Keycloak account not found");
            }
            throw new IdentityProviderUnavailableException();
        } catch (RestClientException exception) {
            throw new IdentityProviderUnavailableException();
        }
    }

    public Set<String> getRealmRoleNames(String subject) {
        try {
            String token = accessToken();
            List<Map<String, Object>> roles = client.get()
                    .uri("/admin/realms/{realm}/users/{id}/role-mappings/realm/composite", realm, subject)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
            if (roles == null) {
                throw new IdentityProviderUnavailableException();
            }
            return roles.stream().map(role -> (String) role.get("name")).collect(Collectors.toSet());
        } catch (RestClientException exception) {
            throw new IdentityProviderUnavailableException();
        }
    }

    public void requireEnabledAccountRole(String subject, SelfRegistrationRole role) {
        KeycloakAccountState state = getAccountState(subject);
        if (!state.enabled()) {
            throw new IllegalStateException("The Keycloak account is disabled; review or recover it before linking a profile");
        }
        if (!getRealmRoleNames(subject).contains(role.name())) {
            throw new IllegalArgumentException("The Keycloak account must have the " + role.name() + " role");
        }
    }

    public void updateAccount(String subject, KeycloakAccountState state) {
        try {
            String token = accessToken();
            client.put()
                    .uri("/admin/realms/{realm}/users/{id}", realm, subject)
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("enabled", state.enabled(), "email", state.email(),
                            "firstName", state.firstName(), "lastName", state.lastName(),
                            "emailVerified", state.emailVerified()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 409) {
                throw new IllegalStateException("The email is already in use in Keycloak");
            }
            throw new IdentityProviderUnavailableException();
        } catch (RestClientException exception) {
            throw new IdentityProviderUnavailableException();
        }
    }

    private String accessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<String, Object> response = client.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", realm)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null || !(response.get("access_token") instanceof String token)) {
            throw new IdentityProviderUnavailableException();
        }
        return token;
    }
}
