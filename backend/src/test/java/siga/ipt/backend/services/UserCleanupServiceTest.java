package siga.ipt.backend.services;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static siga.ipt.backend.services.ServiceFixtures.*;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import siga.ipt.backend.entitys.User;
import siga.ipt.backend.repositories.AcademicPeriodRepository;
import siga.ipt.backend.repositories.StudentRepository;
import siga.ipt.backend.repositories.TeacherRepository;
import siga.ipt.backend.repositories.UserRepository;
import siga.ipt.backend.dtos.KeycloakAccountState;
import siga.ipt.backend.dtos.SelfRegistrationRole;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class UserCleanupServiceTest extends ServiceTestSupport {

    @Mock private StudentRepository students;
    @Mock private TeacherRepository teachers;
    @Mock private AcademicPeriodRepository periods;
    @Mock private UserRepository users;
    @Mock private KeycloakRegistrationService keycloak;
    @InjectMocks private UserCleanupService service;

    private final KeycloakAccountState previous = new KeycloakAccountState(
            true, "person@example.test", "Test", "Person", true);

    @BeforeEach
    void transaction() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "true,false,false", "false,true,false", "false,false,true"})
    void suspendsOnlyUsersWithoutRemainingProfilesOrHistoricalReferences(boolean student, boolean teacher, boolean closing) {
        User user = user("subject");
        when(students.existsByUserId(1L)).thenReturn(student);
        if (!student) {
            when(teachers.existsByUserId(1L)).thenReturn(teacher);
            if (!teacher) {
                when(periods.existsByClosedById(1L)).thenReturn(closing);
            }
        }
        if (!student && !teacher && !closing) {
            when(keycloak.getRealmRoleNames("subject")).thenReturn(Set.of("STUDENT"));
            when(keycloak.getAccountState("subject")).thenReturn(previous);
        }
        service.suspendIfUnreferenced(user);
        verify(users, student || teacher || closing ? never() : times(1)).save(user);
        verify(users, never()).delete(any());
        assertEquals(!student && !teacher && !closing, user.isAccessSuspended());
        if (user.isAccessSuspended()) {
            assertNotNull(user.getTokensValidAfter());
            assertEquals(0, user.getTokensValidAfter().getNano());
            assertTrue(user.getTokensValidAfter().isAfter(java.time.Instant.now().minusSeconds(1)));
        } else {
            assertNull(user.getTokensValidAfter());
        }
    }

    @Test
    void neverDisablesAnAdministratorAccount() {
        when(keycloak.getRealmRoleNames("subject")).thenReturn(Set.of("ADMIN", "TEACHER"));
        service.suspendIfUnreferenced(user("subject"));
        verify(keycloak, never()).updateAccount(any(), any());
        verifyNoInteractions(users);
    }

    @Test
    void synchronizesNamesAndEmailWithoutPreservingVerificationOfANewEmail() {
        User user = user("subject");
        when(keycloak.getAccountState("subject")).thenReturn(previous);
        service.synchronizeIdentity(user, "Updated Name", "new@example.test");
        verify(keycloak).updateAccount("subject", new KeycloakAccountState(
                true, "new@example.test", "Updated", "Name", false));
        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(keycloak, never()).updateAccount("subject", previous);
    }

    @Test
    void preservesVerificationForTheSameEmailAndSupportsSingleNames() {
        when(keycloak.getAccountState("subject")).thenReturn(previous);
        service.synchronizeIdentity(user("subject"), "Person", "person@example.test");
        verify(keycloak).updateAccount("subject", new KeycloakAccountState(
                true, "person@example.test", "Person", "Person", true));
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(keycloak).updateAccount("subject", previous);
    }

    @Test
    void unchangedIdentityDoesNotCallKeycloak() {
        User user = user("subject");
        service.synchronizeIdentity(user, user.getFullName(), user.getEmail());
        verifyNoInteractions(keycloak);
    }

    @Test
    void restoresAccessOnlyForTheExistingAcademicRoleAndNeverForAnAdministrator() {
        User user = user("subject");
        user.setAccessSuspended(true);
        var cutoff = java.time.Instant.parse("2026-10-28T12:00:01Z");
        user.setTokensValidAfter(cutoff);
        when(keycloak.getRealmRoleNames("subject")).thenReturn(Set.of("ADMIN"));
        assertThrows(IllegalStateException.class, () -> service.restoreAccess(user, SelfRegistrationRole.STUDENT));
        when(keycloak.getRealmRoleNames("subject")).thenReturn(Set.of("TEACHER"));
        assertThrows(IllegalStateException.class, () -> service.restoreAccess(user, SelfRegistrationRole.STUDENT));
        when(keycloak.getRealmRoleNames("subject")).thenReturn(Set.of("STUDENT"));
        when(keycloak.getAccountState("subject")).thenReturn(previous.withEnabled(false));
        service.restoreAccess(user, SelfRegistrationRole.STUDENT);
        assertFalse(user.isAccessSuspended());
        assertEquals(cutoff, user.getTokensValidAfter());
        verify(users).save(user);
        verify(keycloak).updateAccount("subject", previous);
    }

    @Test
    void failedCompensationDoesNotMaskTheOriginalTransactionFailure() {
        when(keycloak.getRealmRoleNames("subject")).thenReturn(Set.of("STUDENT"));
        when(keycloak.getAccountState("subject")).thenReturn(previous);
        service.suspendIfUnreferenced(user("subject"));
        doThrow(new IllegalStateException("offline")).when(keycloak).updateAccount("subject", previous);
        assertDoesNotThrow(() -> complete(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }
}
