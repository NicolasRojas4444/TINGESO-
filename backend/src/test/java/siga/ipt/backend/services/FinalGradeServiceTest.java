package siga.ipt.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static siga.ipt.backend.services.ServiceFixtures.*;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.access.AccessDeniedException;
import siga.ipt.backend.dtos.FinalGradeDto;
import siga.ipt.backend.entitys.CourseRegistration;
import siga.ipt.backend.entitys.FinalGrade;
import siga.ipt.backend.entitys.enums.GradeResult;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.entitys.enums.RegistrationStatus;
import siga.ipt.backend.repositories.AcademicPeriodRepository;
import siga.ipt.backend.repositories.CourseRegistrationRepository;
import siga.ipt.backend.repositories.FinalGradeRepository;
import siga.ipt.backend.repositories.SectionRepository;

class FinalGradeServiceTest extends ServiceTestSupport {

    @Mock private FinalGradeRepository grades;
    @Mock private CourseRegistrationRepository registrations;
    @Mock private AcademicPeriodRepository periods;
    @Mock private SectionRepository sections;
    @InjectMocks private FinalGradeService service;

    private CourseRegistration editable() {
        CourseRegistration registration = registration(1, section(1, course(1, 1)));
        when(registrations.findAcademicPeriodIdById(1L)).thenReturn(Optional.of(1L));
        when(periods.findByIdForUpdate(1L)).thenReturn(Optional.of(registration.getSection().getAcademicPeriod()));
        when(registrations.findById(1L)).thenReturn(Optional.of(registration));
        when(currentUser.subject()).thenReturn("teacher-subject");
        return registration;
    }

    private FinalGradeDto input(String value) {
        return new FinalGradeDto(null, null, null, null, value == null ? null : new BigDecimal(value), null);
    }

    @ParameterizedTest
    @CsvSource({"1.0,FAILED", "3.9,FAILED", "4.0,PASSED", "7.0,PASSED"})
    void recordsBoundaryGradesAndDerivesTheResult(String value, GradeResult result) {
        editable();
        when(grades.save(any())).thenAnswer(call -> identified(call.getArgument(0), 1));
        FinalGradeDto saved = service.create(1L, input(value));
        assertEquals(new BigDecimal(value), saved.value());
        assertEquals(result, saved.result());
        verify(currentUser).requireRole("TEACHER");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0.9", "7.1", "3.99"})
    void rejectsMissingOutOfRangeOrExcessPrecisionGrades(String value) {
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, input(value)));
        verifyNoInteractions(grades, registrations);
    }

    @Test
    void rejectsMissingBodiesAndUnknownResources() {
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, null));
        assertThrows(IllegalArgumentException.class, () -> service.create(null, input("5")));
        assertThrows(EntityNotFoundException.class, () -> service.create(99L, input("5")));
        when(registrations.findAcademicPeriodIdById(1L)).thenReturn(Optional.of(1L));
        assertThrows(EntityNotFoundException.class, () -> service.create(1L, input("5")));
        assertThrows(EntityNotFoundException.class, () -> service.getByRegistration(99L));
        assertThrows(IllegalArgumentException.class, () -> service.getBySection(null));
        assertThrows(EntityNotFoundException.class, () -> service.getBySection(99L));
    }

    @Test
    void updatesExistingGradesButRejectsDuplicateCreationAndMissingGrades() {
        CourseRegistration registration = editable();
        FinalGrade entity = grade(registration, "3.0");
        when(grades.existsByRegistrationId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.create(1L, input("5")));
        assertThrows(EntityNotFoundException.class, () -> service.update(1L, input("5")));
        when(grades.findByRegistrationId(1L)).thenReturn(Optional.of(entity));
        when(grades.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(GradeResult.PASSED, service.update(1L, input("5")).result());
        assertEquals(new BigDecimal("5.0"), entity.getValue());
    }

    @Test
    void rejectsOtherTeachersCancelledRegistrationsAndClosedPeriods() {
        CourseRegistration registration = editable();
        when(currentUser.subject()).thenReturn("another-teacher");
        assertThrows(AccessDeniedException.class, () -> service.create(1L, input("5")));
        when(currentUser.subject()).thenReturn("teacher-subject");
        registration.setStatus(RegistrationStatus.CANCELLED);
        assertThrows(IllegalStateException.class, () -> service.create(1L, input("5")));
        registration.setStatus(RegistrationStatus.ACTIVE);
        registration.getSection().getAcademicPeriod().setStatus(PeriodStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> service.update(1L, input("5")));
        verify(grades, never()).save(any());
    }

    @Test
    void allowsAdminAndResponsibleTeacherReadsAndSectionGradeListing() {
        CourseRegistration registration = registration(1, section(1, course(1, 1)));
        when(registrations.findById(1L)).thenReturn(Optional.of(registration));
        when(grades.findByRegistrationId(1L)).thenReturn(Optional.of(grade(registration, "5.0")));
        when(currentUser.hasRole("ADMIN")).thenReturn(true);
        assertEquals(GradeResult.PASSED, service.getByRegistration(1L).result());
        when(currentUser.hasRole("ADMIN")).thenReturn(false);
        when(currentUser.hasRole("TEACHER")).thenReturn(true);
        when(currentUser.subject()).thenReturn("teacher-subject");
        assertEquals(1L, service.getByRegistration(1L).registrationId());
        when(sections.findById(1L)).thenReturn(Optional.of(registration.getSection()));
        when(grades.findByRegistrationSectionIdAndRegistrationStatus(1L, RegistrationStatus.ACTIVE))
                .thenReturn(List.of(grade(registration, "3.0")));
        assertEquals(GradeResult.FAILED, service.getBySection(1L).getFirst().result());
        verify(currentUser).requireTeacherAccess(1L);
    }

    @Test
    void studentsReadOnlyTheirOwnClosedPeriodGrades() {
        CourseRegistration registration = registration(1, section(1, course(1, 1)));
        when(registrations.findById(1L)).thenReturn(Optional.of(registration));
        when(currentUser.hasRole(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(call -> "STUDENT".equals(call.getArgument(0)));
        assertThrows(AccessDeniedException.class, () -> service.getByRegistration(1L));
        registration.getSection().getAcademicPeriod().setStatus(PeriodStatus.CLOSED);
        when(currentUser.studentId()).thenReturn(2L);
        assertThrows(AccessDeniedException.class, () -> service.getByRegistration(1L));
        when(currentUser.studentId()).thenReturn(1L);
        when(grades.findByRegistrationId(1L)).thenReturn(Optional.of(grade(registration, "4.0")));
        assertEquals(GradeResult.PASSED, service.getByRegistration(1L).result());
    }
}
