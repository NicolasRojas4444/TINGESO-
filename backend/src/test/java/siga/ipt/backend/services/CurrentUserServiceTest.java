package siga.ipt.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static siga.ipt.backend.services.ServiceFixtures.*;

import jakarta.persistence.EntityNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import siga.ipt.backend.repositories.StudentRepository;
import siga.ipt.backend.repositories.TeacherRepository;
import siga.ipt.backend.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    @Mock private StudentRepository students;
    @Mock private TeacherRepository teachers;
    @Mock private UserRepository users;
    @InjectMocks private CurrentUserService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String subject, String... roles) {
        Jwt.Builder token = Jwt.withTokenValue("unit-token").header("alg", "RS256")
                .claim("realm_access", Map.of("roles", Arrays.asList(roles)));
        if (subject != null) {
            token.subject(subject);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token.build(), List.of()));
    }

    @Test
    void readsSubjectAndRealmRolesAndRejectsMissingRoles() {
        authenticate("subject", "STUDENT");
        assertEquals("subject", service.subject());
        assertTrue(service.hasRole("STUDENT"));
        assertFalse(service.hasRole("ADMIN"));
        assertThrows(AccessDeniedException.class, () -> service.requireRole("ADMIN"));
        service.requireAcademicUser();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "TEACHER", "STUDENT"})
    void acceptsEachAcademicRole(String role) {
        authenticate("subject", role);
        assertDoesNotThrow(service::requireAcademicUser);
    }

    @Test
    void acceptsMappedAuthoritiesWithoutRealmRoles() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("subject").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertTrue(service.hasRole("ADMIN"));
        service.requireStudentAccess(9L);
        service.requireTeacherAccess(9L);
        service.requireSectionAccess(99L, "another-teacher");
        verifyNoInteractions(students, teachers);
    }

    @Test
    void rejectsNonAcademicAndMalformedRealmRoles() {
        for (Object realm : List.of("invalid", Map.of("roles", "ADMIN"), Map.of("roles", List.of("OTHER")))) {
            Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("subject")
                    .claim("realm_access", realm).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
            assertFalse(service.hasRole("ADMIN"));
            assertThrows(AccessDeniedException.class, service::requireAcademicUser);
        }
    }

    @Test
    void enforcesStudentOwnershipAndReportsMissingProfiles() {
        authenticate("student-subject", "STUDENT");
        when(students.findIdByUserSubject("student-subject")).thenReturn(Optional.of(1L));
        assertEquals(1L, service.studentId());
        service.requireStudentAccess(1L);
        assertThrows(AccessDeniedException.class, () -> service.requireOwnStudent(2L));
        assertThrows(AccessDeniedException.class, () -> service.requireOwnStudent(null));
        when(students.findIdByUserSubject("student-subject")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, service::studentId);
    }

    @Test
    void enforcesTeacherOwnershipAndReportsMissingProfiles() {
        authenticate("teacher-subject", "TEACHER");
        when(teachers.findByUserKeycloakSubject("teacher-subject")).thenReturn(Optional.of(teacher()));
        assertEquals(1L, service.teacherId());
        service.requireTeacherAccess(1L);
        assertThrows(AccessDeniedException.class, () -> service.requireTeacherAccess(2L));
        assertThrows(AccessDeniedException.class, () -> service.requireTeacherAccess(null));
        when(teachers.findByUserKeycloakSubject("teacher-subject")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, service::teacherId);
    }

    @Test
    void restrictsSectionAccessToTheResponsibleTeacherOrStudentsPlan() {
        authenticate("teacher-subject", "TEACHER");
        assertTrue(service.canReadSection(99L, "teacher-subject"));
        assertFalse(service.canReadSection(1L, "other"));
        assertThrows(AccessDeniedException.class, () -> service.requireSectionAccess(1L, "other"));
        authenticate("student-subject", "STUDENT");
        when(students.findByUserKeycloakSubject("student-subject")).thenReturn(Optional.of(student()));
        assertTrue(service.canReadSection(1L, "other"));
        assertFalse(service.canReadSection(2L, "other"));
        when(students.findByUserKeycloakSubject("student-subject")).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.canReadSection(1L, "other"));
    }

    @Test
    void rejectsPreviouslyIssuedTokensForLocallySuspendedAccounts() {
        authenticate("student-subject", "STUDENT");
        var suspended = user("student-subject");
        suspended.setAccessSuspended(true);
        when(users.findByKeycloakSubject("student-subject")).thenReturn(Optional.of(suspended));
        assertThrows(AccessDeniedException.class, service::subject);
        suspended.setAccessSuspended(false);
        assertEquals("student-subject", service.subject());
    }

    @Test
    void requiresAnAuthenticatedJwtWithANonBlankSubject() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::subject);
        Authentication authentication = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::subject);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("person", "unused", List.of()));
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::subject);
        authenticate(null);
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::subject);
        authenticate(" ");
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::subject);
    }

    @Test
    void recoveryNeverReacceptsTokensBeforeTheSuspensionCutoff() {
        var cutoff = java.time.Instant.parse("2026-10-28T12:00:01Z");
        var user = user("subject");
        user.setTokensValidAfter(cutoff);
        when(users.findByKeycloakSubject("subject")).thenReturn(Optional.of(user));
        authenticate("subject", "STUDENT");
        assertThrows(org.springframework.security.authentication.CredentialsExpiredException.class, service::subject);
        for (long offset : new long[] {-2, -1, 0, 1}) {
            Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("subject")
                    .issuedAt(cutoff.plusSeconds(offset)).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
            user.setAccessSuspended(true);
            assertThrows(AccessDeniedException.class, service::subject);
            user.setAccessSuspended(false);
            if (offset < 0) {
                assertThrows(org.springframework.security.authentication.CredentialsExpiredException.class, service::subject);
            } else {
                assertEquals("subject", service.subject());
            }
        }
    }
}
