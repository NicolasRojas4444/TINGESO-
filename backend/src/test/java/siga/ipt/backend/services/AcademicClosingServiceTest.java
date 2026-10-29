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
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import siga.ipt.backend.entitys.AcademicPeriod;
import siga.ipt.backend.entitys.AcademicRecord;
import siga.ipt.backend.entitys.CourseRegistration;
import siga.ipt.backend.entitys.Student;
import siga.ipt.backend.entitys.enums.GradeResult;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.entitys.enums.RegistrationStatus;
import siga.ipt.backend.entitys.enums.StudentStatus;
import siga.ipt.backend.repositories.AcademicPeriodRepository;
import siga.ipt.backend.repositories.AcademicRecordRepository;
import siga.ipt.backend.repositories.CourseRegistrationRepository;
import siga.ipt.backend.repositories.CourseRepository;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.FinalGradeRepository;
import siga.ipt.backend.repositories.SectionRepository;
import siga.ipt.backend.repositories.StudentRepository;
import siga.ipt.backend.repositories.UserRepository;
import siga.ipt.backend.exceptions.IncompleteGradesException;

class AcademicClosingServiceTest extends ServiceTestSupport {

    @Mock private DegreeProgramRepository programs;
    @Mock private AcademicPeriodRepository periods;
    @Mock private SectionRepository sections;
    @Mock private CourseRegistrationRepository registrations;
    @Mock private FinalGradeRepository grades;
    @Mock private AcademicRecordRepository records;
    @Mock private StudentRepository students;
    @Mock private CourseRepository courses;
    @Mock private UserRepository users;
    @InjectMocks private AcademicClosingService service;

    private AcademicPeriod openContext() {
        AcademicPeriod period = period();
        when(sections.findDegreeProgramIdsByAcademicPeriodId(1L)).thenReturn(List.of(1L));
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(program()));
        when(periods.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        return period;
    }

    @ParameterizedTest
    @CsvSource({"5.0,false,false,GRADUATED", "3.0,false,false,REGULAR", "3.0,true,false,ELIMINATED", "5.0,false,true,REGULAR"})
    void consolidatesSnapshotsAndUpdatesAcademicStatus(
            String value, boolean previousFailure, boolean unfinishedPlan, StudentStatus expected
    ) {
        AcademicPeriod period = openContext();
        Student student = student();
        CourseRegistration registration = registration(1, section(1, course(1, 1)));
        when(registrations.findBySectionAcademicPeriodIdAndStatus(1L, RegistrationStatus.ACTIVE))
                .thenReturn(List.of(registration));
        when(grades.findByRegistrationSectionAcademicPeriodIdAndRegistrationStatus(1L, RegistrationStatus.ACTIVE))
                .thenReturn(List.of(grade(registration, value)));
        when(students.findByIdForUpdate(1L)).thenReturn(Optional.of(student));
        when(records.findHistoryByStudentId(1L, PeriodStatus.CLOSED)).thenReturn(previousFailure
                ? List.of(history(registration(2, registration.getSection()), GradeResult.FAILED)) : List.of());
        if (expected != StudentStatus.ELIMINATED) {
            when(courses.findByStudyPlanIdOrderBySemesterAscCodeAsc(1L)).thenReturn(unfinishedPlan
                    ? List.of(registration.getSection().getCourse(), course(2, 2))
                    : List.of(registration.getSection().getCourse()));
        }
        when(currentUser.subject()).thenReturn("admin-subject");
        when(users.findByKeycloakSubject("admin-subject")).thenReturn(Optional.of(user("admin-subject")));
        when(periods.save(any())).thenAnswer(call -> call.getArgument(0));

        assertEquals(PeriodStatus.CLOSED, service.close(1L).status());
        assertEquals(expected, student.getAcademicStatus());
        assertNotNull(period.getClosedAt());
        assertEquals("admin-subject", period.getClosedBy().getKeycloakSubject());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AcademicRecord>> captured = ArgumentCaptor.forClass(List.class);
        verify(records).saveAll(captured.capture());
        AcademicRecord snapshot = captured.getValue().getFirst();
        assertSame(registration, snapshot.getRegistration());
        assertEquals("TAP1", snapshot.getCourseCode());
        assertEquals("Course 1", snapshot.getCourseName());
        assertEquals(1, snapshot.getCurriculumSemester());
        assertEquals(2, snapshot.getTheoryHours());
        assertEquals(0, snapshot.getExerciseHours());
        assertEquals(0, snapshot.getLaboratoryHours());
        assertEquals(4, snapshot.getSct());
        assertEquals(value, snapshot.getFinalGrade().toPlainString());
        assertNotNull(snapshot.getConsolidatedAt());
        verify(students).saveAll(any());
        verify(currentUser).requireRole("ADMIN");
    }

    @Test
    void rejectsClosureWithMissingGradesAndReportsIncompleteSections() {
        openContext();
        when(registrations.findBySectionAcademicPeriodIdAndStatus(1L, RegistrationStatus.ACTIVE))
                .thenReturn(List.of(registration(1, section(8, course(1, 1)))));
        IncompleteGradesException error = assertThrows(IncompleteGradesException.class, () -> service.close(1L));
        assertEquals(List.of(8L), error.getSectionIds());
        verify(records, never()).saveAll(any());
        verify(periods, never()).save(any());
    }

    @Test
    void rejectsNullMissingClosedOrConcurrentlyChangedPeriods() {
        assertThrows(IllegalArgumentException.class, () -> service.close(null));
        assertThrows(EntityNotFoundException.class, () -> service.close(99L));
        AcademicPeriod period = openContext();
        period.setStatus(PeriodStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> service.close(1L));
        period.setStatus(PeriodStatus.OPEN);
        when(sections.findDegreeProgramIdsByAcademicPeriodId(1L))
                .thenReturn(List.of(1L))
                .thenReturn(List.of(1L, 2L));
        assertThrows(IllegalStateException.class, () -> service.close(1L));
    }

    @Test
    void refusesToConsolidateTheSameRegistrationTwice() {
        openContext();
        CourseRegistration registration = registration(1, section(1, course(1, 1)));
        when(registrations.findBySectionAcademicPeriodIdAndStatus(1L, RegistrationStatus.ACTIVE)).thenReturn(List.of(registration));
        when(grades.findByRegistrationSectionAcademicPeriodIdAndRegistrationStatus(1L, RegistrationStatus.ACTIVE))
                .thenReturn(List.of(grade(registration, "5.0")));
        when(students.findByIdForUpdate(1L)).thenReturn(Optional.of(student()));
        when(records.existsByRegistrationId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.close(1L));
        verify(records, never()).saveAll(any());
    }
}
