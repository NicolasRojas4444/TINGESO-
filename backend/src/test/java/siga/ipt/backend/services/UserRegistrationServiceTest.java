package siga.ipt.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static siga.ipt.backend.services.ServiceFixtures.*;

import jakarta.persistence.EntityManager;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import siga.ipt.backend.dtos.SelfRegistrationRole;
import siga.ipt.backend.dtos.StudentDto;
import siga.ipt.backend.dtos.TeacherDto;
import siga.ipt.backend.dtos.UserRegistrationRequest;
import siga.ipt.backend.dtos.UserRegistrationResponse;

class UserRegistrationServiceTest extends ServiceTestSupport {

    @Mock private KeycloakRegistrationService keycloak;
    @Mock private StudentService students;
    @Mock private TeacherService teachers;
    @Mock private EntityManager entityManager;
    @InjectMocks private UserRegistrationService service;

    @BeforeEach
    void enableDevelopmentRegistration() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "teacherEnabled", true);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearTransactionSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void createsAStudentProfileAndEnablesTheKeycloakAccountOnlyAfterFlushing() {
        UserRegistrationRequest request = valid(SelfRegistrationRole.STUDENT);
        when(keycloak.createAccount(request)).thenReturn("keycloak-subject");
        StudentDto profile = mapper.toDto(student());
        when(students.createRegisteredProfile(eq(1L), any())).thenReturn(profile);
        UserRegistrationResponse response = service.register(request);
        assertEquals(1L, response.studentId());
        assertNull(response.teacherId());
        assertEquals(SelfRegistrationRole.STUDENT, response.role());
        assertEquals("keycloak-subject", response.keycloakSubject());
        assertEquals("test-user", response.username());
        ArgumentCaptor<StudentDto> captured = ArgumentCaptor.forClass(StudentDto.class);
        verify(students).createRegisteredProfile(eq(1L), captured.capture());
        assertEquals("keycloak-subject", captured.getValue().user().keycloakSubject());
        assertEquals("12345678-5", captured.getValue().user().run());
        var order = inOrder(entityManager, keycloak);
        order.verify(entityManager).flush();
        order.verify(keycloak).enableAccount("keycloak-subject");
        complete(TransactionSynchronization.STATUS_COMMITTED);
        verify(keycloak, never()).rollbackAccount(anyString());
    }

    @Test
    void createsATeacherProfileWithoutProvidingAdministratorPrivileges() {
        UserRegistrationRequest request = valid(SelfRegistrationRole.TEACHER);
        when(keycloak.createAccount(request)).thenReturn("keycloak-subject");
        TeacherDto profile = mapper.toDto(teacher());
        when(teachers.createRegisteredProfile(any())).thenReturn(profile);
        UserRegistrationResponse response = service.register(request);
        assertEquals(1L, response.teacherId());
        assertNull(response.studentId());
        ArgumentCaptor<TeacherDto> captured = ArgumentCaptor.forClass(TeacherDto.class);
        verify(teachers).createRegisteredProfile(captured.capture());
        assertEquals("Engineer", captured.getValue().professionalTitle());
        assertEquals("Bachelor", captured.getValue().academicDegree());
        assertNull(captured.getValue().status());
        verifyNoInteractions(students);
    }

    @Test
    void compensatesKeycloakCreationIfTheAcademicProfileFails() {
        UserRegistrationRequest request = valid(SelfRegistrationRole.STUDENT);
        when(keycloak.createAccount(request)).thenReturn("keycloak-subject");
        when(students.createRegisteredProfile(eq(1L), any())).thenThrow(new IllegalArgumentException("Duplicate RUN"));
        assertThrows(IllegalArgumentException.class, () -> service.register(request));
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(keycloak).rollbackAccount("keycloak-subject");
        verify(keycloak, never()).enableAccount(anyString());
    }

    @Test
    void compensatesIfDatabaseFlushFailsAndNeverEnablesAnUnpersistedProfile() {
        UserRegistrationRequest request = valid(SelfRegistrationRole.STUDENT);
        when(keycloak.createAccount(request)).thenReturn("keycloak-subject");
        StudentDto profile = mapper.toDto(student());
        when(students.createRegisteredProfile(eq(1L), any())).thenReturn(profile);
        doThrow(new DataIntegrityViolationException("Duplicate identity")).when(entityManager).flush();
        assertThrows(DataIntegrityViolationException.class, () -> service.register(request));
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(keycloak).rollbackAccount("keycloak-subject");
        verify(keycloak, never()).enableAccount(anyString());
    }

    @Test
    void disabledRegistrationNeverContactsKeycloak() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThrows(AccessDeniedException.class, () -> service.register(valid(SelfRegistrationRole.STUDENT)));
        verifyNoInteractions(keycloak, students, teachers, entityManager);
    }

    @Test
    void teacherRegistrationCanBeDisabledIndependently() {
        ReflectionTestUtils.setField(service, "teacherEnabled", false);
        assertThrows(AccessDeniedException.class, () -> service.register(valid(SelfRegistrationRole.TEACHER)));
        verifyNoInteractions(keycloak, students, teachers, entityManager);
    }

    @Test
    void redactsCredentialsFromTheRequestStringRepresentation() {
        UserRegistrationRequest request = valid(SelfRegistrationRole.TEACHER);
        assertFalse(request.toString().contains(request.password()));
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void rejectsInvalidRequestsBeforeCreatingExternalAccounts(UserRegistrationRequest request) {
        assertThrows(IllegalArgumentException.class, () -> service.register(request));
        verifyNoInteractions(keycloak, students, teachers, entityManager);
    }

    static Stream<UserRegistrationRequest> invalidRequests() {
        return Stream.of(null,
                request("user", "password1", "Name", "a@example.test", null, 1L, "Title", "Degree"),
                request(null, "password1", "Name", "a@example.test", SelfRegistrationRole.STUDENT, 1L, null, null),
                request("ab", "password1", "Name", "a@example.test", SelfRegistrationRole.STUDENT, 1L, null, null),
                request("x".repeat(81), "password1", "Name", "a@example.test", SelfRegistrationRole.STUDENT, 1L, null, null),
                request("bad user", "password1", "Name", "a@example.test", SelfRegistrationRole.STUDENT, 1L, null, null),
                request("user", null, "Name", "a@example.test", SelfRegistrationRole.STUDENT, 1L, null, null),
                request("user", "        ", "Name", "a@example.test", SelfRegistrationRole.STUDENT, 1L, null, null),
                request("user", "short", "Name", "a@example.test", SelfRegistrationRole.STUDENT, 1L, null, null),
                request("user", "x".repeat(129), "Name", "a@example.test", SelfRegistrationRole.STUDENT, 1L, null, null),
                request("user", "password1", null, "a@example.test", SelfRegistrationRole.STUDENT, 1L, null, null),
                request("user", "password1", " ", "a@example.test", SelfRegistrationRole.STUDENT, 1L, null, null),
                request("user", "password1", "x".repeat(201), "a@example.test", SelfRegistrationRole.STUDENT, 1L, null, null),
                request("user", "password1", "Name", null, SelfRegistrationRole.STUDENT, 1L, null, null),
                request("user", "password1", "Name", "x".repeat(255), SelfRegistrationRole.STUDENT, 1L, null, null),
                request("user", "password1", "Name", "bad-email", SelfRegistrationRole.STUDENT, 1L, null, null),
                request("user", "password1", "Name", "a@example.test", SelfRegistrationRole.STUDENT, null, null, null),
                request("user", "password1", "Name", "a@example.test", SelfRegistrationRole.TEACHER, null, null, "Degree"),
                request("user", "password1", "Name", "a@example.test", SelfRegistrationRole.TEACHER, null, " ", "Degree"),
                request("user", "password1", "Name", "a@example.test", SelfRegistrationRole.TEACHER, null, "Title", null),
                request("user", "password1", "Name", "a@example.test", SelfRegistrationRole.TEACHER, null, "Title", " "));
    }

    private static UserRegistrationRequest valid(SelfRegistrationRole role) {
        return request(" test-user ", "unit-password", "Test Person", "a@example.test", role, 1L, "Engineer", "Bachelor");
    }

    private static UserRegistrationRequest request(String username, String password, String name, String email,
                                                   SelfRegistrationRole role, Long program, String title, String degree) {
        return new UserRegistrationRequest(username, password, "12345678-5", name, email, role, program, title, degree);
    }

    private void complete(int status) {
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(status));
    }
}
