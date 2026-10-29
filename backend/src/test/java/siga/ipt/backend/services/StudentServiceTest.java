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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import siga.ipt.backend.dtos.StudentDto;
import siga.ipt.backend.dtos.UserDto;
import siga.ipt.backend.entitys.DegreeProgram;
import siga.ipt.backend.entitys.Student;
import siga.ipt.backend.entitys.StudyPlan;
import siga.ipt.backend.entitys.User;
import siga.ipt.backend.entitys.enums.ActiveStatus;
import siga.ipt.backend.entitys.enums.PlanStatus;
import siga.ipt.backend.entitys.enums.StudentStatus;
import siga.ipt.backend.repositories.AcademicRecordRepository;
import siga.ipt.backend.repositories.CourseRegistrationRepository;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.EnrollmentRepository;
import siga.ipt.backend.repositories.StudentRepository;
import siga.ipt.backend.repositories.StudyPlanRepository;
import siga.ipt.backend.repositories.UserRepository;

class StudentServiceTest extends ServiceTestSupport {

    @Mock private UserCleanupService cleanup;
    @Mock private KeycloakRegistrationService keycloak;
    @Mock private StudentRepository students;
    @Mock private UserRepository users;
    @Mock private DegreeProgramRepository programs;
    @Mock private StudyPlanRepository plans;
    @Mock private CourseRegistrationRepository registrations;
    @Mock private AcademicRecordRepository history;
    @Mock private EnrollmentRepository enrollments;
    @InjectMocks private StudentService service;

    private Student lockStudent() {
        Student entity = student();
        when(students.findDegreeProgramIdById(1L)).thenReturn(Optional.of(1L));
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(entity.getStudyPlan().getDegreeProgram()));
        when(students.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
        return entity;
    }

    private void arrangeCreation() {
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(program()));
        when(plans.findByDegreeProgramIdAndStatus(1L, PlanStatus.CURRENT)).thenReturn(List.of(plan()));
    }

    private void saveEntities() {
        when(users.save(any())).thenAnswer(call -> identified(call.getArgument(0), 1));
        when(students.save(any())).thenAnswer(call -> identified(call.getArgument(0), 1));
    }

    private StudentDto input(UserDto user) {
        return new StudentDto(null, user, 99L, 99L, StudentStatus.ELIMINATED);
    }

    @Test
    void createsRegularStudentsOnTheCurrentPlanAndNormalizesIdentity() {
        arrangeCreation();
        saveEntities();
        StudentDto result = service.create(1L, input(IdentityValidationCases.validUser()));
        assertEquals(StudentStatus.REGULAR, result.academicStatus());
        assertEquals(1L, result.studyPlanId());
        assertEquals("12345678-5", result.user().run());
        assertEquals("person@example.test", result.user().email());
        assertEquals("Test Person", result.user().fullName());
        assertEquals("subject", result.user().keycloakSubject());
        verify(currentUser).requireRole("ADMIN");
        verify(keycloak).requireEnabledAccountRole("subject", siga.ipt.backend.dtos.SelfRegistrationRole.STUDENT);
    }

    @Test
    void registrationWorkflowCreatesAProfileWithoutChangingAdministratorEndpoints() {
        arrangeCreation();
        saveEntities();
        StudentDto result = service.createRegisteredProfile(1L, input(IdentityValidationCases.validUser()));
        assertEquals(StudentStatus.REGULAR, result.academicStatus());
        assertEquals(1L, result.studyPlanId());
        verifyNoInteractions(currentUser, keycloak);
    }

    @Test
    void rejectsInvalidKeycloakAccountsBeforePersistingAnAdministrativeProfile() {
        arrangeCreation();
        doThrow(new EntityNotFoundException("Keycloak account not found")).when(keycloak)
                .requireEnabledAccountRole("subject", siga.ipt.backend.dtos.SelfRegistrationRole.STUDENT);
        assertThrows(EntityNotFoundException.class, () -> service.create(1L, input(IdentityValidationCases.validUser())));
        verify(users, never()).save(any());
        verify(students, never()).save(any());
    }

    @Test
    void restoresTheRetainedIdentityWithoutCreatingADuplicateUser() {
        arrangeCreation();
        when(students.save(any())).thenAnswer(call -> identified(call.getArgument(0), 2));
        User retained = user("retained-subject");
        StudentDto result = service.restoreProfile(1L, retained);
        assertEquals(2L, result.id());
        assertEquals("retained-subject", result.user().keycloakSubject());
        assertEquals(StudentStatus.REGULAR, result.academicStatus());
        verifyNoInteractions(users);
    }

    @Test
    void readsStudentsAndUpdatesOnlyEditablePersonalInformation() {
        Student entity = lockStudent();
        when(students.findAll()).thenReturn(List.of(entity));
        when(students.findById(1L)).thenReturn(Optional.of(entity));
        saveEntities();
        when(users.findByRun("12345678-5")).thenReturn(Optional.of(entity.getUser()));
        when(users.findByEmailIgnoreCase("person@example.test")).thenReturn(Optional.of(entity.getUser()));
        assertEquals(1, service.getAll().size());
        assertEquals(1L, service.getById(1L).id());
        StudentDto result = service.update(1L, input(IdentityValidationCases.validUser()));
        assertEquals(StudentStatus.REGULAR, result.academicStatus());
        assertEquals(1L, result.studyPlanId());
        assertEquals("student-subject", result.user().keycloakSubject());
    }

    @ParameterizedTest
    @MethodSource("siga.ipt.backend.services.IdentityValidationCases#invalidUsers")
    void rejectsInvalidIdentity(UserDto user) {
        // Subject validation occurs after validating the program and current plan.
        if (user != null && user.email() != null && user.email().equals("a@example.test")
                && "12345678-5".equals(user.run()) && "Name".equals(user.fullName())) {
            arrangeCreation();
        }
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, input(user)));
        verify(users, never()).save(any());
    }

    @Test
    void rejectsMissingStudentDataAndUnknownResources() {
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, null));
        assertThrows(IllegalArgumentException.class, () -> service.getById(null));
        assertThrows(EntityNotFoundException.class, () -> service.getById(99L));
        assertThrows(IllegalArgumentException.class, () -> service.update(null, input(IdentityValidationCases.validUser())));
        assertThrows(EntityNotFoundException.class, () -> service.update(99L, input(IdentityValidationCases.validUser())));
        assertThrows(IllegalArgumentException.class, () -> service.create(null, input(IdentityValidationCases.validUser())));
        assertThrows(EntityNotFoundException.class, () -> service.create(99L, input(IdentityValidationCases.validUser())));
    }

    @Test
    void rejectsInactiveProgramsAndMissingOrAmbiguousCurrentPlans() {
        DegreeProgram program = program();
        program.setStatus(ActiveStatus.INACTIVE);
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(program));
        assertThrows(IllegalStateException.class, () -> service.create(1L, input(IdentityValidationCases.validUser())));
        program.setStatus(ActiveStatus.ACTIVE);
        when(plans.findByDegreeProgramIdAndStatus(1L, PlanStatus.CURRENT)).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> service.create(1L, input(IdentityValidationCases.validUser())));
        when(plans.findByDegreeProgramIdAndStatus(1L, PlanStatus.CURRENT)).thenReturn(List.of(plan(), plan()));
        assertThrows(IllegalStateException.class, () -> service.create(1L, input(IdentityValidationCases.validUser())));
    }

    @Test
    void rejectsRunEmailAndKeycloakSubjectCollisions() {
        arrangeCreation();
        User other = identified(user("other"), 8);
        when(users.findByRun("12345678-5")).thenReturn(Optional.of(other));
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, input(IdentityValidationCases.validUser())));
        when(users.findByRun("12345678-5")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("person@example.test")).thenReturn(Optional.of(other));
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, input(IdentityValidationCases.validUser())));
        when(users.findByEmailIgnoreCase("person@example.test")).thenReturn(Optional.empty());
        when(users.existsByKeycloakSubject("subject")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, input(IdentityValidationCases.validUser())));
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = StudentStatus.class, names = {"REGULAR", "POSTPONED", "TEMPORARY_WITHDRAWAL"})
    void allowsOnlyAdministrativeStatuses(StudentStatus status) {
        lockStudent();
        when(students.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(status, service.changeStatus(1L, new StudentDto(null, null, null, null, status)).academicStatus());
    }

    @Test
    void rejectsManualGraduationEliminationAndChangingTerminalStatuses() {
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(1L, null));
        for (StudentStatus terminal : List.of(StudentStatus.GRADUATED, StudentStatus.ELIMINATED)) {
            assertThrows(IllegalArgumentException.class, () -> service.changeStatus(1L,
                    new StudentDto(null, null, null, null, terminal)));
        }
        Student entity = lockStudent();
        entity.setAcademicStatus(StudentStatus.ELIMINATED);
        assertThrows(IllegalStateException.class, () -> service.changeStatus(1L,
                new StudentDto(null, null, null, null, StudentStatus.REGULAR)));
    }

    @Test
    void changesThePlanOnlyWithoutAcademicActivity() {
        Student entity = lockStudent();
        when(plans.findDegreeProgramIdById(2L)).thenReturn(Optional.of(1L));
        StudyPlan target = identified(plan(), 2);
        when(plans.findById(2L)).thenReturn(Optional.of(target));
        when(students.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(2L, service.changeStudyPlan(1L, 2L).studyPlanId());
        assertSame(target, entity.getStudyPlan());
        when(registrations.existsByStudentId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.changeStudyPlan(1L, 2L));
    }

    @Test
    void rejectsInvalidPlanChangesAndConcurrentCareerChanges() {
        assertThrows(IllegalArgumentException.class, () -> service.changeStudyPlan(null, 1L));
        assertThrows(IllegalArgumentException.class, () -> service.changeStudyPlan(1L, null));
        assertThrows(EntityNotFoundException.class, () -> service.changeStudyPlan(99L, 1L));
        when(students.findDegreeProgramIdById(1L)).thenReturn(Optional.of(1L));
        assertThrows(EntityNotFoundException.class, () -> service.changeStudyPlan(1L, 99L));
        Student entity = lockStudent();
        entity.getStudyPlan().setDegreeProgram(identified(program(), 2));
        when(plans.findDegreeProgramIdById(2L)).thenReturn(Optional.of(1L));
        assertThrows(IllegalStateException.class, () -> service.changeStudyPlan(1L, 2L));
    }

    @Test
    void protectsStudentsWithRegistrationsHistoryOrEnrollmentAndCleansUpAnUnusedUser() {
        Student entity = lockStudent();
        when(registrations.existsByStudentId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        when(registrations.existsByStudentId(1L)).thenReturn(false);
        when(history.existsByRegistrationStudentId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        when(history.existsByRegistrationStudentId(1L)).thenReturn(false);
        when(enrollments.existsByStudentId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        when(enrollments.existsByStudentId(1L)).thenReturn(false);
        service.delete(1L);
        verify(students).delete(entity);
        verify(students).flush();
        verify(cleanup).suspendIfUnreferenced(entity.getUser());
    }
}
