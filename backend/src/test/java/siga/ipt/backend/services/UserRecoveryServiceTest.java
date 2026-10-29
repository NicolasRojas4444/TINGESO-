package siga.ipt.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static siga.ipt.backend.services.ServiceFixtures.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.access.AccessDeniedException;
import siga.ipt.backend.dtos.SelfRegistrationRole;
import siga.ipt.backend.dtos.UserProfileRecoveryRequest;
import siga.ipt.backend.entitys.User;
import siga.ipt.backend.repositories.AcademicPeriodRepository;
import siga.ipt.backend.repositories.StudentRepository;
import siga.ipt.backend.repositories.TeacherRepository;
import siga.ipt.backend.repositories.UserRepository;

class UserRecoveryServiceTest extends ServiceTestSupport {

    @Mock private UserRepository users;
    @Mock private StudentRepository students;
    @Mock private TeacherRepository teachers;
    @Mock private AcademicPeriodRepository periods;
    @Mock private StudentService studentService;
    @Mock private TeacherService teacherService;
    @Mock private UserCleanupService accounts;
    @Mock private EntityManager entityManager;
    @Mock private KeycloakRegistrationService keycloak;
    @InjectMocks private UserRecoveryService service;

    private User suspended() {
        User user = user("subject");
        user.setAccessSuspended(true);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void listsRetainedSuspendedIdentitiesOnlyForAdministrators() {
        when(users.findByAccessSuspendedTrue()).thenReturn(List.of(user("subject")));
        assertEquals("subject", service.getSuspendedUsers().getFirst().keycloakSubject());
        verify(currentUser).requireRole("ADMIN");
    }

    @Test
    void restoresAStudentWithTheSameRetainedIdentity() {
        User user = suspended();
        var profile = mapper.toDto(student());
        when(studentService.restoreProfile(1L, user)).thenReturn(profile);
        var result = service.restoreProfile(1L, request(SelfRegistrationRole.STUDENT));
        assertEquals(1L, result.studentId());
        assertNull(result.teacherId());
        assertEquals("subject", result.keycloakSubject());
        var order = inOrder(entityManager, accounts);
        order.verify(entityManager).flush();
        order.verify(accounts).restoreAccess(user, SelfRegistrationRole.STUDENT);
    }

    @Test
    void restoresATeacherWithoutCreatingANewKeycloakAccount() {
        User user = suspended();
        var profile = mapper.toDto(teacher());
        when(teacherService.restoreProfile(user, "Engineer", "Bachelor")).thenReturn(profile);
        var result = service.restoreProfile(1L, request(SelfRegistrationRole.TEACHER));
        assertEquals(1L, result.teacherId());
        assertNull(result.studentId());
        verify(accounts).restoreAccess(user, SelfRegistrationRole.TEACHER);
        verifyNoInteractions(studentService);
    }

    @Test
    void requiresAnExistingSuspendedUnreferencedUserAndValidRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.restoreProfile(null, request(SelfRegistrationRole.STUDENT)));
        assertThrows(IllegalArgumentException.class, () -> service.restoreProfile(1L, null));
        assertThrows(IllegalArgumentException.class, () -> service.restoreProfile(1L, request(null)));
        assertThrows(EntityNotFoundException.class, () -> service.restoreProfile(99L, request(SelfRegistrationRole.STUDENT)));
        User user = suspended();
        user.setAccessSuspended(false);
        assertThrows(IllegalStateException.class, () -> service.restoreProfile(1L, request(SelfRegistrationRole.STUDENT)));
        user.setAccessSuspended(true);
        when(students.existsByUserId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.restoreProfile(1L, request(SelfRegistrationRole.STUDENT)));
        when(students.existsByUserId(1L)).thenReturn(false);
        when(teachers.existsByUserId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.restoreProfile(1L, request(SelfRegistrationRole.TEACHER)));
        when(teachers.existsByUserId(1L)).thenReturn(false);
        when(periods.existsByClosedById(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.restoreProfile(1L, request(SelfRegistrationRole.STUDENT)));
        verifyNoInteractions(accounts);
    }

    @Test
    void rejectsNonAdministratorsBeforeAccessingTheRetainedIdentity() {
        doThrow(new AccessDeniedException("ADMIN required")).when(currentUser).requireRole("ADMIN");
        assertThrows(AccessDeniedException.class, service::getSuspendedUsers);
        assertThrows(AccessDeniedException.class, () -> service.restoreProfile(1L, request(SelfRegistrationRole.STUDENT)));
        assertThrows(AccessDeniedException.class, () -> service.linkProfile(1L, request(SelfRegistrationRole.TEACHER)));
        assertThrows(AccessDeniedException.class, () -> service.getLinkCandidates(SelfRegistrationRole.TEACHER));
        verifyNoInteractions(users, accounts);
    }

    @Test
    void linksActiveIdentitiesWithoutChangingAccessRolesOrTokenCutoff() {
        User user = user("subject");
        var cutoff = java.time.Instant.parse("2026-10-28T12:00:01Z");
        user.setTokensValidAfter(cutoff);
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        var teacherProfile = mapper.toDto(teacher());
        when(teacherService.restoreProfile(user, "Engineer", "Bachelor")).thenReturn(teacherProfile);
        var teacherResult = service.linkProfile(1L, request(SelfRegistrationRole.TEACHER));
        assertEquals(1L, teacherResult.teacherId());
        assertNull(teacherResult.studentId());
        var studentProfile = mapper.toDto(student());
        when(studentService.restoreProfile(1L, user)).thenReturn(studentProfile);
        var studentResult = service.linkProfile(1L, request(SelfRegistrationRole.STUDENT));
        assertEquals(1L, studentResult.studentId());
        assertNull(studentResult.teacherId());
        assertEquals(cutoff, user.getTokensValidAfter());
        assertFalse(user.isAccessSuspended());
        verify(keycloak).requireEnabledAccountRole("subject", SelfRegistrationRole.TEACHER);
        verify(keycloak).requireEnabledAccountRole("subject", SelfRegistrationRole.STUDENT);
        verifyNoMoreInteractions(keycloak);
        verifyNoInteractions(accounts, periods);
        verify(entityManager, times(2)).flush();
        verify(users, never()).save(any());
    }

    @Test
    void filtersLocalCandidatesByRequestedMissingProfile() {
        User first = user("first");
        User second = identified(user("second"), 2);
        when(users.findByAccessSuspendedFalse()).thenReturn(List.of(first, second));
        when(students.existsByUserId(1L)).thenReturn(true);
        when(students.existsByUserId(2L)).thenReturn(false);
        when(teachers.existsByUserId(1L)).thenReturn(false);
        when(teachers.existsByUserId(2L)).thenReturn(true);
        assertEquals("second", service.getLinkCandidates(SelfRegistrationRole.STUDENT).getFirst().keycloakSubject());
        assertEquals("first", service.getLinkCandidates(SelfRegistrationRole.TEACHER).getFirst().keycloakSubject());
        assertThrows(IllegalArgumentException.class, () -> service.getLinkCandidates(null));
        verifyNoInteractions(keycloak);
    }

    @Test
    void rejectsInvalidLinkRequestsSuspensionDuplicatesAndInvalidProviderAccounts() {
        assertThrows(IllegalArgumentException.class, () -> service.linkProfile(null, request(SelfRegistrationRole.STUDENT)));
        assertThrows(IllegalArgumentException.class, () -> service.linkProfile(1L, null));
        assertThrows(IllegalArgumentException.class, () -> service.linkProfile(1L, request(null)));
        assertThrows(EntityNotFoundException.class, () -> service.linkProfile(99L, request(SelfRegistrationRole.TEACHER)));
        User user = suspended();
        assertThrows(IllegalStateException.class, () -> service.linkProfile(1L, request(SelfRegistrationRole.TEACHER)));
        user.setAccessSuspended(false);
        when(students.existsByUserId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.linkProfile(1L, request(SelfRegistrationRole.STUDENT)));
        when(teachers.existsByUserId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.linkProfile(1L, request(SelfRegistrationRole.TEACHER)));
        when(teachers.existsByUserId(1L)).thenReturn(false);
        doThrow(new IllegalStateException("Disabled account")).when(keycloak)
                .requireEnabledAccountRole("subject", SelfRegistrationRole.TEACHER);
        assertThrows(IllegalStateException.class, () -> service.linkProfile(1L, request(SelfRegistrationRole.TEACHER)));
        verifyNoInteractions(studentService, teacherService, entityManager, accounts);
    }

    private UserProfileRecoveryRequest request(SelfRegistrationRole role) {
        return new UserProfileRecoveryRequest(role, 1L, "Engineer", "Bachelor");
    }
}
