package siga.ipt.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static siga.ipt.backend.services.ServiceFixtures.*;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import siga.ipt.backend.dtos.TeacherDto;
import siga.ipt.backend.dtos.UserDto;
import siga.ipt.backend.entitys.Teacher;
import siga.ipt.backend.entitys.User;
import siga.ipt.backend.entitys.enums.ActiveStatus;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.repositories.SectionRepository;
import siga.ipt.backend.repositories.TeacherRepository;
import siga.ipt.backend.repositories.UserRepository;

class TeacherServiceTest extends ServiceTestSupport {

    @Mock private TeacherRepository teachers;
    @Mock private UserRepository users;
    @Mock private SectionRepository sections;
    @Mock private UserCleanupService cleanup;
    @Mock private KeycloakRegistrationService keycloak;
    @InjectMocks private TeacherService service;

    private TeacherDto input(UserDto user) {
        return new TeacherDto(null, user, " Engineer ", " Bachelor ", ActiveStatus.INACTIVE);
    }

    @Test
    void createsActiveTeachersWithNormalizedIdentityAndQualifications() {
        when(users.save(any())).thenAnswer(call -> identified(call.getArgument(0), 1));
        when(teachers.save(any())).thenAnswer(call -> identified(call.getArgument(0), 1));
        TeacherDto result = service.create(input(IdentityValidationCases.validUser()));
        assertEquals(ActiveStatus.ACTIVE, result.status());
        assertEquals("12345678-5", result.user().run());
        assertEquals("person@example.test", result.user().email());
        assertEquals("subject", result.user().keycloakSubject());
        assertEquals("Test Person", result.user().fullName());
        assertEquals("Engineer", result.professionalTitle());
        assertEquals("Bachelor", result.academicDegree());
        verify(keycloak).requireEnabledAccountRole("subject", siga.ipt.backend.dtos.SelfRegistrationRole.TEACHER);
    }

    @Test
    void registrationWorkflowCreatesAnActiveAcademicProfile() {
        when(users.save(any())).thenAnswer(call -> identified(call.getArgument(0), 1));
        when(teachers.save(any())).thenAnswer(call -> identified(call.getArgument(0), 1));
        TeacherDto result = service.createRegisteredProfile(input(IdentityValidationCases.validUser()));
        assertEquals(ActiveStatus.ACTIVE, result.status());
        assertEquals("subject", result.user().keycloakSubject());
        verifyNoInteractions(currentUser, keycloak);
    }

    @Test
    void rejectsInvalidKeycloakAccountsBeforePersistingAnAdministrativeProfile() {
        doThrow(new IllegalArgumentException("Missing TEACHER role")).when(keycloak)
                .requireEnabledAccountRole("subject", siga.ipt.backend.dtos.SelfRegistrationRole.TEACHER);
        assertThrows(IllegalArgumentException.class, () -> service.create(input(IdentityValidationCases.validUser())));
        verify(users, never()).save(any());
        verify(teachers, never()).save(any());
    }

    @Test
    void restoresAProfileUsingTheRetainedUserAndValidatedQualifications() {
        when(teachers.save(any())).thenAnswer(call -> identified(call.getArgument(0), 2));
        TeacherDto result = service.restoreProfile(user("retained"), " Engineer ", " Bachelor ");
        assertEquals("retained", result.user().keycloakSubject());
        assertEquals("Engineer", result.professionalTitle());
        assertEquals("Bachelor", result.academicDegree());
        assertEquals(ActiveStatus.ACTIVE, result.status());
        verifyNoInteractions(users);
    }

    @Test
    void listsReadsAndUpdatesExistingTeachersWithoutChangingTheirSubjectOrStatus() {
        Teacher entity = teacher();
        when(teachers.findAll()).thenReturn(List.of(entity));
        when(teachers.findById(1L)).thenReturn(Optional.of(entity));
        when(teachers.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
        when(teachers.save(any())).thenAnswer(call -> call.getArgument(0));
        when(users.findByRun("12345678-5")).thenReturn(Optional.of(entity.getUser()));
        when(users.findByEmailIgnoreCase("person@example.test")).thenReturn(Optional.of(entity.getUser()));
        assertEquals(1, service.getAll().size());
        assertEquals(1L, service.getById(1L).id());
        TeacherDto result = service.update(1L, input(IdentityValidationCases.validUser()));
        assertEquals("teacher-subject", result.user().keycloakSubject());
        assertEquals(ActiveStatus.ACTIVE, result.status());
        verify(users).save(entity.getUser());
    }

    @ParameterizedTest
    @MethodSource("siga.ipt.backend.services.IdentityValidationCases#invalidUsers")
    void rejectsInvalidIdentity(UserDto user) {
        assertThrows(IllegalArgumentException.class, () -> service.create(input(user)));
        verify(teachers, never()).save(any());
    }

    @Test
    void requiresAllTeacherDataAndValidIds() {
        assertThrows(IllegalArgumentException.class, () -> service.create(null));
        UserDto user = IdentityValidationCases.validUser();
        for (String invalid : new String[]{null, " ", "x".repeat(201)}) {
            assertThrows(IllegalArgumentException.class, () -> service.create(new TeacherDto(null, user, invalid, "Bachelor", null)));
            assertThrows(IllegalArgumentException.class, () -> service.create(new TeacherDto(null, user, "Engineer", invalid, null)));
        }
        assertThrows(IllegalArgumentException.class, () -> service.getById(null));
        assertThrows(EntityNotFoundException.class, () -> service.getById(99L));
        assertThrows(IllegalArgumentException.class, () -> service.delete(null));
        assertThrows(EntityNotFoundException.class, () -> service.delete(99L));
    }

    @Test
    void rejectsDuplicateRunEmailAndKeycloakSubject() {
        User other = identified(user("other"), 8);
        when(users.findByRun("12345678-5")).thenReturn(Optional.of(other));
        assertThrows(IllegalArgumentException.class, () -> service.create(input(IdentityValidationCases.validUser())));
        when(users.findByRun("12345678-5")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("person@example.test")).thenReturn(Optional.of(other));
        assertThrows(IllegalArgumentException.class, () -> service.create(input(IdentityValidationCases.validUser())));
        when(users.findByEmailIgnoreCase("person@example.test")).thenReturn(Optional.empty());
        when(users.existsByKeycloakSubject("subject")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(input(IdentityValidationCases.validUser())));
        verify(users, never()).save(any());
    }

    @Test
    void preventsInactivationWithOpenSectionsAndPreservesHistoricReferences() {
        Teacher entity = teacher();
        when(teachers.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
        when(sections.existsByTeacherIdAndAcademicPeriodStatus(1L, PeriodStatus.OPEN)).thenReturn(true);
        TeacherDto inactive = new TeacherDto(null, null, null, null, ActiveStatus.INACTIVE);
        assertThrows(IllegalStateException.class, () -> service.changeStatus(1L, inactive));
        assertEquals(ActiveStatus.ACTIVE, entity.getStatus());
        when(sections.existsByTeacherIdAndAcademicPeriodStatus(1L, PeriodStatus.OPEN)).thenReturn(false);
        when(teachers.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(ActiveStatus.INACTIVE, service.changeStatus(1L, inactive).status());
        assertEquals(ActiveStatus.ACTIVE, service.changeStatus(1L,
                new TeacherDto(null, null, null, null, ActiveStatus.ACTIVE)).status());
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(1L, null));
    }

    @Test
    void preventsDeletingReferencedTeachersAndCleansUpUnusedProfiles() {
        Teacher entity = teacher();
        when(teachers.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
        when(sections.existsByTeacherId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        verify(teachers, never()).delete(any());
        when(sections.existsByTeacherId(1L)).thenReturn(false);
        service.delete(1L);
        verify(teachers).delete(entity);
        verify(teachers).flush();
        verify(cleanup).suspendIfUnreferenced(entity.getUser());
    }
}
