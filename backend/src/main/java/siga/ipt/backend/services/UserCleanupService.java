package siga.ipt.backend.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import siga.ipt.backend.dtos.KeycloakAccountState;
import siga.ipt.backend.dtos.SelfRegistrationRole;
import siga.ipt.backend.entitys.User;
import siga.ipt.backend.repositories.AcademicPeriodRepository;
import siga.ipt.backend.repositories.StudentRepository;
import siga.ipt.backend.repositories.TeacherRepository;
import siga.ipt.backend.repositories.UserRepository;

@Service
@RequiredArgsConstructor
public class UserCleanupService {

    private static final Logger log = LoggerFactory.getLogger(UserCleanupService.class);

    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final AcademicPeriodRepository periods;
    private final UserRepository users;
    private final KeycloakRegistrationService keycloak;

    @Transactional
    public void suspendIfUnreferenced(User user) {
        if (students.existsByUserId(user.getId()) || teachers.existsByUserId(user.getId())
                || periods.existsByClosedById(user.getId())) {
            return;
        }
        if (keycloak.getRealmRoleNames(user.getKeycloakSubject()).contains("ADMIN")) {
            return;
        }
        KeycloakAccountState previous = keycloak.getAccountState(user.getKeycloakSubject());
        restoreOnRollback(user.getKeycloakSubject(), previous);
        keycloak.updateAccount(user.getKeycloakSubject(), previous.withEnabled(false));
        user.setAccessSuspended(true);
        // JWT iat has second precision. Reject the entire suspension second,
        // including tokens issued just before suspension in that same second.
        user.setTokensValidAfter(Instant.now().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1));
        users.save(user);
    }

    @Transactional
    public void synchronizeIdentity(User user, String fullName, String email) {
        if (user.getFullName().equals(fullName) && user.getEmail().equals(email)) {
            return;
        }
        KeycloakAccountState previous = keycloak.getAccountState(user.getKeycloakSubject());
        int separator = fullName.lastIndexOf(' ');
        KeycloakAccountState updated = new KeycloakAccountState(previous.enabled(), email,
                separator > 0 ? fullName.substring(0, separator) : fullName,
                separator > 0 ? fullName.substring(separator + 1) : fullName,
                previous.emailVerified() && previous.email().equalsIgnoreCase(email));
        restoreOnRollback(user.getKeycloakSubject(), previous);
        keycloak.updateAccount(user.getKeycloakSubject(), updated);
    }

    @Transactional
    public void restoreAccess(User user, SelfRegistrationRole role) {
        var roles = keycloak.getRealmRoleNames(user.getKeycloakSubject());
        if (roles.contains("ADMIN") || !roles.contains(role.name())) {
            throw new IllegalStateException("Account roles require manual review in Keycloak before recovery");
        }
        KeycloakAccountState previous = keycloak.getAccountState(user.getKeycloakSubject());
        restoreOnRollback(user.getKeycloakSubject(), previous);
        keycloak.updateAccount(user.getKeycloakSubject(), previous.withEnabled(true));
        user.setAccessSuspended(false);
        users.save(user);
    }

    private void restoreOnRollback(String subject, KeycloakAccountState previous) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        keycloak.updateAccount(subject, previous);
                    } catch (RuntimeException exception) {
                        log.error("Could not compensate Keycloak account {}; manual reconciliation is required", subject);
                    }
                }
            }
        });
    }
}
