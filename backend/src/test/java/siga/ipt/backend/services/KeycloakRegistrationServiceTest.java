package siga.ipt.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import siga.ipt.backend.dtos.SelfRegistrationRole;
import siga.ipt.backend.dtos.KeycloakAccountState;
import java.util.Set;
import java.io.IOException;
import siga.ipt.backend.dtos.UserRegistrationRequest;
import siga.ipt.backend.exceptions.IdentityProviderUnavailableException;

class KeycloakRegistrationServiceTest {

    private static final String BASE = "http://identity-provider.test";
    private MockRestServiceServer server;
    private KeycloakRegistrationService service;

    @BeforeEach
    void createAnInMemoryHttpMockWithoutNetworkConnections() {
        RestClient.Builder actualBuilder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(actualBuilder).build();
        RestClient mockHttpClient = actualBuilder.build();
        RestClient.Builder constructorBuilder = mock(RestClient.Builder.class, RETURNS_SELF);
        when(constructorBuilder.build()).thenReturn(mockHttpClient);
        try (MockedStatic<RestClient> factory = mockStatic(RestClient.class)) {
            factory.when(RestClient::builder).thenReturn(constructorBuilder);
            service = new KeycloakRegistrationService(BASE, "siga", "registration-client", "unit-secret", 10);
        }
    }

    private void token() {
        server.expect(requestTo(BASE + "/realms/siga/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"unit-access-token\"}", MediaType.APPLICATION_JSON));
    }

    private void userCreated() {
        server.expect(requestTo(BASE + "/admin/realms/siga/users"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertTrue(body.contains("unit-password"));
                    assertTrue(body.contains("\"enabled\":false"));
                    assertTrue(body.contains("a@example.test"));
                })
                .andRespond(withStatus(HttpStatus.CREATED)
                        .location(URI.create(BASE + "/admin/realms/siga/users/subject-1")));
    }

    private void rollback() {
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());
    }

    private UserRegistrationRequest input(String name) {
        return new UserRegistrationRequest(" test-user ", "unit-password", "12345678-5", name,
                " A@EXAMPLE.TEST ", SelfRegistrationRole.STUDENT, 1L, null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Test Person", "Person"})
    void createsADisabledAccountAndAssignsOnlyTheRequestedAllowedRole(String name) {
        token();
        userCreated();
        server.expect(requestTo(BASE + "/admin/realms/siga/roles/STUDENT"))
                .andRespond(withSuccess("{\"id\":\"role-1\",\"name\":\"STUDENT\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST)).andRespond(withNoContent());
        assertEquals("subject-1", service.createAccount(input(name)));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 409, 503})
    void translatesProviderErrorsWithoutLeakingUpstreamDetails(int status) {
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).body("sensitive-upstream-details"));
        Class<? extends RuntimeException> type = status == 400 ? IllegalArgumentException.class
                : status == 409 ? IllegalStateException.class : IdentityProviderUnavailableException.class;
        RuntimeException error = assertThrows(type, () -> service.createAccount(input("Test Person")));
        assertFalse(error.getMessage().contains("sensitive-upstream-details"));
        assertFalse(error.getMessage().contains("unit-password"));
        server.verify();
    }

    @Test
    void compensatesIfRoleAssignmentFailsAfterAccountCreation() {
        token();
        userCreated();
        server.expect(requestTo(BASE + "/admin/realms/siga/roles/STUDENT"))
                .andRespond(withSuccess("{\"id\":\"role-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1/role-mappings/realm"))
                .andRespond(withServerError());
        rollback();
        assertThrows(IdentityProviderUnavailableException.class, () -> service.createAccount(input("Test Person")));
        server.verify();
    }

    @Test
    void compensatesIfTheRequestedRoleCannotBeRead() {
        token();
        userCreated();
        server.expect(requestTo(BASE + "/admin/realms/siga/roles/STUDENT")).andRespond(withNoContent());
        rollback();
        assertThrows(IdentityProviderUnavailableException.class, () -> service.createAccount(input("Test Person")));
        server.verify();
    }

    @Test
    void rejectsCreationResponsesWithoutALocation() {
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users")).andRespond(withStatus(HttpStatus.CREATED));
        assertThrows(IdentityProviderUnavailableException.class, () -> service.createAccount(input("Test Person")));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"access_token\":12}", ""})
    void refusesMissingOrMalformedServiceTokens(String body) {
        server.expect(requestTo(BASE + "/realms/siga/protocol/openid-connect/token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThrows(IdentityProviderUnavailableException.class, () -> service.createAccount(input("Test Person")));
        server.verify();
    }

    @Test
    void enablesPersistedAccountsAndTranslatesActivationFailures() {
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1"))
                .andExpect(method(HttpMethod.PUT)).andRespond(withNoContent());
        service.enableAccount("subject-1");
        server.verify();
        server.reset();
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1")).andRespond(withServerError());
        assertThrows(IdentityProviderUnavailableException.class, () -> service.enableAccount("subject-1"));
        server.verify();
    }

    @Test
    void readsCredentialFreeAccountStateAndEffectiveRealmRoles() {
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1"))
                .andRespond(withSuccess("{\"enabled\":true,\"email\":\"a@example.test\",\"firstName\":\"Test\",\"lastName\":\"Person\",\"emailVerified\":true}", MediaType.APPLICATION_JSON));
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1/role-mappings/realm/composite"))
                .andRespond(withSuccess("[{\"name\":\"ADMIN\"},{\"name\":\"TEACHER\"}]", MediaType.APPLICATION_JSON));
        assertEquals(new KeycloakAccountState(true, "a@example.test", "Test", "Person", true), service.getAccountState("subject-1"));
        assertEquals(Set.of("ADMIN", "TEACHER"), service.getRealmRoleNames("subject-1"));
        server.verify();
    }

    @Test
    void updatesAccountMetadataWithoutIncludingPasswords() {
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(request -> {
                    String body = ((MockClientHttpRequest) request).getBodyAsString();
                    assertFalse(body.contains("password"));
                    assertTrue(body.contains("\"enabled\":false"));
                }).andRespond(withNoContent());
        service.updateAccount("subject-1", new KeycloakAccountState(false, "new@example.test", "New", "Name", false));
        server.verify();
    }

    @Test
    void rejectsMissingAccountStateOrRoleResponses() {
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1")).andRespond(withNoContent());
        assertThrows(IdentityProviderUnavailableException.class, () -> service.getAccountState("subject-1"));
        server.verify();
        server.reset();
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1/role-mappings/realm/composite"))
                .andRespond(withNoContent());
        assertThrows(IdentityProviderUnavailableException.class, () -> service.getRealmRoleNames("subject-1"));
        server.verify();
    }

    @Test
    void translatesRoleAccountAndMetadataUpdateFailures() {
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1")).andRespond(withServerError());
        assertThrows(IdentityProviderUnavailableException.class, () -> service.getAccountState("subject-1"));
        server.verify();
        server.reset();
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1/role-mappings/realm/composite"))
                .andRespond(withServerError());
        assertThrows(IdentityProviderUnavailableException.class, () -> service.getRealmRoleNames("subject-1"));
        server.verify();
        server.reset();
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1")).andRespond(withStatus(HttpStatus.CONFLICT));
        KeycloakAccountState state = new KeycloakAccountState(true, "email", "Name", "Person", false);
        assertThrows(IllegalStateException.class, () -> service.updateAccount("subject-1", state));
        server.verify();
        server.reset();
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1")).andRespond(withServerError());
        assertThrows(IdentityProviderUnavailableException.class, () -> service.updateAccount("subject-1", state));
        server.verify();
        server.reset();
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1"))
                .andRespond(request -> { throw new IOException("unavailable"); });
        assertThrows(IdentityProviderUnavailableException.class, () -> service.updateAccount("subject-1", state));
        server.verify();
    }

    @Test
    void rollsBackAccountsAndDoesNotMaskTheOriginalFailureIfCleanupIsUnavailable() {
        rollback();
        service.rollbackAccount("subject-1");
        server.verify();
        server.reset();
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1")).andRespond(withServerError());
        assertDoesNotThrow(() -> service.rollbackAccount("subject-1"));
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "disabled", "wrong-role", "missing"})
    void validatesExistingEnabledAccountsAndTheirEffectiveRole(String scenario) {
        token();
        var account = server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1"));
        if (scenario.equals("missing")) {
            account.andRespond(withStatus(HttpStatus.NOT_FOUND));
        } else {
            account.andRespond(withSuccess("{\"enabled\":" + !scenario.equals("disabled") + "}", MediaType.APPLICATION_JSON));
        }
        if (scenario.equals("valid") || scenario.equals("wrong-role")) {
            token();
            server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1/role-mappings/realm/composite"))
                    .andRespond(withSuccess(scenario.equals("valid")
                            ? "[{\"name\":\"ADMIN\"},{\"name\":\"TEACHER\"}]" : "[{\"name\":\"STUDENT\"}]", MediaType.APPLICATION_JSON));
        }
        switch (scenario) {
            case "valid" -> assertDoesNotThrow(() -> service.requireEnabledAccountRole("subject-1", SelfRegistrationRole.TEACHER));
            case "disabled" -> assertThrows(IllegalStateException.class,
                    () -> service.requireEnabledAccountRole("subject-1", SelfRegistrationRole.TEACHER));
            case "wrong-role" -> assertThrows(IllegalArgumentException.class,
                    () -> service.requireEnabledAccountRole("subject-1", SelfRegistrationRole.TEACHER));
            default -> assertThrows(jakarta.persistence.EntityNotFoundException.class,
                    () -> service.requireEnabledAccountRole("subject-1", SelfRegistrationRole.TEACHER));
        }
        server.verify();
    }

    @Test
    void refusesAccountValidationWhenTheProviderCannotBeReached() {
        token();
        server.expect(requestTo(BASE + "/admin/realms/siga/users/subject-1"))
                .andRespond(request -> { throw new IOException("connection unavailable"); });
        assertThrows(IdentityProviderUnavailableException.class,
                () -> service.requireEnabledAccountRole("subject-1", SelfRegistrationRole.STUDENT));
        server.verify();
    }
}
