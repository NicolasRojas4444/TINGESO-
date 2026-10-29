package siga.ipt.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static siga.ipt.backend.services.ServiceFixtures.*;

import jakarta.persistence.EntityNotFoundException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import siga.ipt.backend.dtos.CourseRegistrationDto;
import siga.ipt.backend.dtos.CourseSelectionRequest;
import siga.ipt.backend.entitys.AcademicPeriod;
import siga.ipt.backend.entitys.Course;
import siga.ipt.backend.entitys.CoursePrerequisite;
import siga.ipt.backend.entitys.CourseRegistration;
import siga.ipt.backend.entitys.FinalGrade;
import siga.ipt.backend.entitys.Section;
import siga.ipt.backend.entitys.Student;
import siga.ipt.backend.entitys.enums.GradeResult;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.entitys.enums.RegistrationStatus;
import siga.ipt.backend.entitys.enums.StudentStatus;
import siga.ipt.backend.repositories.AcademicPeriodRepository;
import siga.ipt.backend.repositories.AcademicRecordRepository;
import siga.ipt.backend.repositories.CoursePrerequisiteRepository;
import siga.ipt.backend.repositories.CourseRegistrationRepository;
import siga.ipt.backend.repositories.CourseRepository;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.EnrollmentRepository;
import siga.ipt.backend.repositories.FinalGradeRepository;
import siga.ipt.backend.repositories.SectionRepository;
import siga.ipt.backend.repositories.StudentRepository;

class CourseRegistrationServiceTest extends ServiceTestSupport {

    @Mock private StudentRepository students;
    @Mock private DegreeProgramRepository programs;
    @Mock private AcademicPeriodRepository periods;
    @Mock private EnrollmentRepository enrollments;
    @Mock private CourseRepository courses;
    @Mock private CoursePrerequisiteRepository prerequisites;
    @Mock private SectionRepository sections;
    @Mock private CourseRegistrationRepository registrations;
    @Mock private AcademicRecordRepository records;
    @Mock private FinalGradeRepository grades;
    @InjectMocks private CourseRegistrationService service;

    private Student student;
    private AcademicPeriod period;
    private List<Course> curriculum;
    private List<Section> offering;
    private List<CourseRegistration> enrolled;

    @BeforeEach
    void fixtures() {
        student = student();
        period = period();
        curriculum = new ArrayList<>();
        offering = new ArrayList<>();
        enrolled = new ArrayList<>();
        for (int id = 1; id <= 3; id++) {
            addOffering(id, 1);
        }
    }

    private void addOffering(int id, int semester) {
        Course course = course(id, semester);
        curriculum.add(course);
        offering.add(section(id, course));
    }

    private void context() {
        // Shared existence fixtures are lenient because rejection scenarios stop at different stages.
        lenient().when(students.findDegreeProgramIdById(1L)).thenReturn(Optional.of(1L));
        lenient().when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(program()));
        lenient().when(periods.findByIdForUpdate(1L)).thenReturn(Optional.of(period));
        lenient().when(periods.findById(1L)).thenReturn(Optional.of(period));
        lenient().when(students.findByIdForUpdate(1L)).thenReturn(Optional.of(student));
        lenient().when(students.findById(1L)).thenReturn(Optional.of(student));
        lenient().when(enrollments.existsByStudentIdAndAcademicPeriodId(1L, 1L)).thenReturn(true);
        lenient().when(courses.findByStudyPlanIdOrderBySemesterAscCodeAsc(1L)).thenReturn(curriculum);
        lenient().when(sections.findByAcademicPeriodId(1L)).thenReturn(offering);
        lenient().when(prerequisites.findByCourseId(anyLong())).thenReturn(List.of());
        lenient().when(registrations.countBySectionIdAndStatus(anyLong(), eq(RegistrationStatus.ACTIVE))).thenReturn(0L);
        lenient().when(sections.findById(anyLong())).thenAnswer(call -> offering.stream()
                .filter(section -> section.getId().equals(call.getArgument(0))).findFirst());
        lenient().when(registrations.findByStudentIdAndSectionAcademicPeriodIdAndStatus(1L, 1L, RegistrationStatus.ACTIVE))
                .thenAnswer(call -> enrolled.stream().filter(item -> item.getStatus() == RegistrationStatus.ACTIVE).toList());
    }

    private void saveRegistrations() {
        when(registrations.save(any())).thenAnswer(call -> {
            CourseRegistration saved = call.getArgument(0);
            if (saved.getId() == null) {
                identified(saved, 10 + enrolled.size());
                enrolled.add(saved);
            }
            return saved;
        });
    }

    private List<CourseRegistrationDto> select(Long... ids) {
        return Arrays.stream(ids).map(id -> new CourseRegistrationDto(null, null, id, null, null,
                null, null, null, null, null)).toList();
    }

    private void arrangeCancellation(CourseRegistration registration) {
        when(registrations.findStudentIdById(registration.getId())).thenReturn(Optional.of(1L));
        when(registrations.findAcademicPeriodIdById(registration.getId())).thenReturn(Optional.of(1L));
        when(registrations.findById(registration.getId())).thenReturn(Optional.of(registration));
    }

    @Test
    void atomicallyReplacesAConflictingCourseWithoutDroppingBelowTheMinimum() {
        context();
        addOffering(4, 1);
        offering.get(3).getScheduleBlocks().getFirst().setModule(1);
        for (int index = 0; index < 3; index++) {
            enrolled.add(registration(index + 1, offering.get(index)));
        }
        CourseRegistration removed = enrolled.getFirst();
        CourseRegistration retained = enrolled.get(1);
        var retainedDate = retained.getRegisteredAt();
        when(registrations.countBySectionIdAndStatus(2L, RegistrationStatus.ACTIVE)).thenReturn(30L);
        assertTrue(service.getAvailableSections(1L, 1L).isEmpty());
        assertEquals(4, service.getReplacementOptions(1L, 1L).size());
        FinalGrade provisional = grade(removed, "5.0");
        when(grades.findByRegistrationId(1L)).thenReturn(Optional.of(provisional));
        saveRegistrations();

        var result = service.replaceSelection(1L, 1L, new CourseSelectionRequest(List.of(2L, 3L, 4L)));
        assertEquals(List.of(2L, 3L, 4L), result.stream().map(CourseRegistrationDto::sectionId).toList());
        assertEquals(RegistrationStatus.CANCELLED, removed.getStatus());
        assertNotNull(removed.getCancelledAt());
        assertEquals(retainedDate, retained.getRegisteredAt());
        verify(registrations, never()).save(retained);
        verify(grades).delete(provisional);
    }

    @Test
    void validatesTheCompleteReplacementBeforeChangingAnyRegistration() {
        context();
        addOffering(4, 1);
        for (int index = 0; index < 3; index++) {
            enrolled.add(registration(index + 1, offering.get(index)));
        }
        when(registrations.countBySectionIdAndStatus(4L, RegistrationStatus.ACTIVE)).thenReturn(30L);
        assertThrows(IllegalStateException.class, () -> service.replaceSelection(1L, 1L,
                new CourseSelectionRequest(List.of(2L, 3L, 4L))));
        when(registrations.countBySectionIdAndStatus(4L, RegistrationStatus.ACTIVE)).thenReturn(0L);
        offering.get(3).getScheduleBlocks().getFirst().setModule(3);
        assertThrows(IllegalArgumentException.class, () -> service.replaceSelection(1L, 1L,
                new CourseSelectionRequest(List.of(2L, 3L, 4L))));
        assertTrue(enrolled.stream().allMatch(item -> item.getStatus() == RegistrationStatus.ACTIVE));
        verify(registrations, never()).save(any());
        verifyNoInteractions(grades);
    }

    @Test
    void reusesCancelledRowsAndLeavesUnchangedSelectionsUntouched() {
        context();
        addOffering(4, 1);
        for (int index = 0; index < 4; index++) {
            enrolled.add(registration(index + 1, offering.get(index)));
        }
        CourseRegistration cancelled = enrolled.getLast();
        cancelled.setStatus(RegistrationStatus.CANCELLED);
        cancelled.setCancelledAt(java.time.Instant.now());
        assertEquals(3, service.replaceSelection(1L, 1L, new CourseSelectionRequest(List.of(1L, 2L, 3L))).size());
        verify(registrations, never()).save(any());
        when(registrations.findByStudentIdAndSectionId(1L, 4L)).thenReturn(Optional.of(cancelled));
        saveRegistrations();
        assertEquals(3, service.replaceSelection(1L, 1L, new CourseSelectionRequest(List.of(2L, 3L, 4L))).size());
        assertEquals(RegistrationStatus.ACTIVE, cancelled.getStatus());
        assertNull(cancelled.getCancelledAt());
        assertEquals(4L, cancelled.getId());
    }

    @Test
    void rejectsMalformedReplacementSelectionsAndIneligibleStudents() {
        context();
        assertThrows(IllegalArgumentException.class, () -> service.replaceSelection(1L, 1L, null));
        assertThrows(IllegalArgumentException.class, () -> service.replaceSelection(1L, 1L, new CourseSelectionRequest(null)));
        assertThrows(IllegalArgumentException.class, () -> service.replaceSelection(1L, 1L, new CourseSelectionRequest(List.of())));
        assertThrows(IllegalArgumentException.class, () -> service.replaceSelection(1L, 1L,
                new CourseSelectionRequest(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L))));
        assertThrows(IllegalArgumentException.class, () -> service.replaceSelection(1L, 1L,
                new CourseSelectionRequest(Arrays.asList((Long) null))));
        assertThrows(IllegalArgumentException.class, () -> service.replaceSelection(1L, 1L,
                new CourseSelectionRequest(List.of(1L, 1L))));
        assertThrows(IllegalArgumentException.class, () -> service.replaceSelection(1L, 1L,
                new CourseSelectionRequest(List.of(1L, 2L))));
        student.setAcademicStatus(StudentStatus.POSTPONED);
        assertThrows(IllegalStateException.class, () -> service.replaceSelection(1L, 1L,
                new CourseSelectionRequest(List.of(1L, 2L, 3L))));
        student.setAcademicStatus(StudentStatus.REGULAR);
        when(enrollments.existsByStudentIdAndAcademicPeriodId(1L, 1L)).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.replaceSelection(1L, 1L,
                new CourseSelectionRequest(List.of(1L, 2L, 3L))));
        verify(registrations, never()).save(any());
    }

    @Test
    void replacementRejectsForeignPlansAndForeignPeriods() {
        context();
        offering.getFirst().getCourse().setStudyPlan(identified(plan(), 2));
        assertThrows(IllegalArgumentException.class, () -> service.replaceSelection(1L, 1L,
                new CourseSelectionRequest(List.of(1L, 2L, 3L))));
        offering.getFirst().getCourse().setStudyPlan(plan());
        offering.getFirst().setAcademicPeriod(identified(period(), 2));
        assertThrows(IllegalArgumentException.class, () -> service.replaceSelection(1L, 1L,
                new CourseSelectionRequest(List.of(1L, 2L, 3L))));
        verify(registrations, never()).save(any());
    }

    @Test
    void registersAValidThreeCourseSelectionWithDateAndAllAssociations() {
        context();
        saveRegistrations();
        List<CourseRegistrationDto> result = service.register(1L, 1L, select(1L, 2L, 3L));
        assertEquals(3, result.size());
        assertTrue(result.stream().allMatch(item -> item.status() == RegistrationStatus.ACTIVE
                && item.registeredAt() != null && item.studentId() == 1L && item.academicPeriodId() == 1L));
        assertTrue(enrolled.stream().allMatch(item -> item.getStudent() == student && item.getCancelledAt() == null));
        verify(currentUser).requireOwnStudent(1L);
        verify(registrations, times(3)).save(any());
    }

    @Test
    void permitsFewerThanThreeCoursesOnlyWhenTheCurriculumIsAlmostComplete() {
        context();
        saveRegistrations();
        when(records.findHistoryByStudentId(1L, PeriodStatus.CLOSED)).thenReturn(List.of(
                history(registration(20, offering.get(0)), GradeResult.PASSED),
                history(registration(21, offering.get(1)), GradeResult.PASSED)));
        assertEquals(1, service.register(1L, 1L, select(3L)).size());
    }

    @Test
    void permitsOneRetakeButNeverAPassedOrTwiceFailedCourse() {
        context();
        when(records.findHistoryByStudentId(1L, PeriodStatus.CLOSED)).thenReturn(List.of(
                history(registration(20, offering.get(0)), GradeResult.PASSED)));
        assertThrows(IllegalStateException.class, () -> service.register(1L, 1L, select(1L)));
        when(records.findHistoryByStudentId(1L, PeriodStatus.CLOSED)).thenReturn(List.of(
                history(registration(20, offering.get(0)), GradeResult.FAILED),
                history(registration(21, offering.get(0)), GradeResult.FAILED)));
        assertThrows(IllegalStateException.class, () -> service.register(1L, 1L, select(1L)));
        when(records.findHistoryByStudentId(1L, PeriodStatus.CLOSED)).thenReturn(List.of(
                history(registration(20, offering.get(0)), GradeResult.FAILED)));
        saveRegistrations();
        assertEquals(3, service.register(1L, 1L, select(1L, 2L, 3L)).size());
    }

    @Test
    void checksAllPrerequisitesAgainstClosedPeriodHistory() {
        context();
        CoursePrerequisite link = new CoursePrerequisite();
        link.setCourse(curriculum.get(1));
        link.setPrerequisite(curriculum.get(0));
        when(prerequisites.findByCourseId(2L)).thenReturn(List.of(link));
        assertThrows(IllegalStateException.class, () -> service.register(1L, 1L, select(1L, 2L, 3L)));
        when(records.findHistoryByStudentId(1L, PeriodStatus.CLOSED)).thenReturn(List.of(
                history(registration(20, offering.get(0)), GradeResult.PASSED)));
        saveRegistrations();
        assertEquals(2, service.register(1L, 1L, select(2L, 3L)).size());
    }

    @Test
    void rejectsNonRegularUnenrolledAndClosedPeriodRegistrations() {
        context();
        student.setAcademicStatus(StudentStatus.POSTPONED);
        assertThrows(IllegalStateException.class, () -> service.register(1L, 1L, select(1L, 2L, 3L)));
        student.setAcademicStatus(StudentStatus.REGULAR);
        when(enrollments.existsByStudentIdAndAcademicPeriodId(1L, 1L)).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.register(1L, 1L, select(1L, 2L, 3L)));
        period.setStatus(PeriodStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> service.register(1L, 1L, select(1L, 2L, 3L)));
        verify(registrations, never()).save(any());
    }

    @Test
    void rejectsEmptyOversizedMalformedAndDuplicateSelections() {
        context();
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, null));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, select(1L,2L,3L,4L,5L,6L,7L)));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, Arrays.asList((CourseRegistrationDto) null)));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, select((Long) null)));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, select(1L, 1L)));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, select(1L, 2L)));
        verify(registrations, never()).save(any());
    }

    @Test
    void rejectsAlreadyRegisteredForeignPlanAndForeignPeriodSections() {
        context();
        enrolled.add(registration(1, offering.get(0)));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, select(1L)));
        enrolled.clear();
        offering.get(0).getCourse().setStudyPlan(identified(plan(), 2));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, select(1L)));
        offering.get(0).getCourse().setStudyPlan(plan());
        offering.get(0).setAcademicPeriod(identified(period(), 2));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, select(1L)));
    }

    @Test
    void rejectsFullSectionsAndConflictingSchedulesWithoutSavingPartialSelections() {
        context();
        when(registrations.countBySectionIdAndStatus(2L, RegistrationStatus.ACTIVE)).thenReturn(30L);
        assertThrows(IllegalStateException.class, () -> service.register(1L, 1L, select(1L, 2L, 3L)));
        when(registrations.countBySectionIdAndStatus(2L, RegistrationStatus.ACTIVE)).thenReturn(0L);
        offering.get(1).getScheduleBlocks().getFirst().setModule(1);
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, select(1L, 2L, 3L)));
        verify(registrations, never()).save(any());
    }

    @Test
    void requiresAvailablePendingEarlierSemesterCoursesBeforeHigherSemesterCourses() {
        context();
        addOffering(4, 2);
        curriculum.get(1).setSemester(2);
        curriculum.get(2).setSemester(2);
        assertThrows(IllegalStateException.class, () -> service.register(1L, 1L, select(2L, 3L, 4L)));
    }

    @Test
    void rejectsDuplicateCoursesAndResultingLoadsAboveSix() {
        context();
        offering.get(1).setCourse(curriculum.get(0));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, select(1L, 2L, 3L)));
        offering.get(1).setCourse(curriculum.get(1));
        for (int id = 4; id <= 7; id++) {
            addOffering(id, 1);
        }
        for (int index = 0; index < 4; index++) {
            enrolled.add(registration(index + 1, offering.get(index)));
        }
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, 1L, select(5L, 6L, 7L)));
    }

    @Test
    void listsRegistrationsAndFiltersAvailableChoicesByCapacityAndSchedule() {
        context();
        CourseRegistration first = registration(1, offering.get(0));
        enrolled.add(first);
        when(registrations.findByStudentIdOrderByRegisteredAtDesc(1L)).thenReturn(List.of(first));
        when(registrations.findBySectionIdAndStatus(1L, RegistrationStatus.ACTIVE)).thenReturn(List.of(first));
        assertEquals(1, service.getByStudent(1L).size());
        assertEquals(1, service.getBySection(1L).size());
        offering.get(1).getScheduleBlocks().getFirst().setModule(1);
        assertEquals(List.of(3L), service.getAvailableSections(1L, 1L).stream().map(item -> item.id()).toList());
        when(registrations.countBySectionIdAndStatus(3L, RegistrationStatus.ACTIVE)).thenReturn(30L);
        assertTrue(service.getAvailableSections(1L, 1L).isEmpty());
        verify(currentUser).requireTeacherAccess(1L);
    }

    @Test
    void cancelsARegistrationReleasesItsSeatAndRemovesItsProvisionalGrade() {
        context();
        addOffering(4, 1);
        for (int index = 0; index < 4; index++) {
            enrolled.add(registration(index + 1, offering.get(index)));
        }
        CourseRegistration first = enrolled.getFirst();
        arrangeCancellation(first);
        FinalGrade grade = grade(first, "5.0");
        when(grades.findByRegistrationId(1L)).thenReturn(Optional.of(grade));
        saveRegistrations();
        assertEquals(RegistrationStatus.CANCELLED, service.cancel(1L).status());
        assertNotNull(first.getCancelledAt());
        verify(grades).delete(grade);
        assertThrows(IllegalStateException.class, () -> service.cancel(1L));
    }

    @Test
    void cancellationCannotDropBelowTheMinimumOrRemovePendingAvailableFinalCourses() {
        context();
        for (int index = 0; index < 3; index++) {
            enrolled.add(registration(index + 1, offering.get(index)));
        }
        arrangeCancellation(enrolled.getFirst());
        assertThrows(IllegalStateException.class, () -> service.cancel(1L));
        when(records.findHistoryByStudentId(1L, PeriodStatus.CLOSED)).thenReturn(List.of(
                history(registration(20, offering.get(1)), GradeResult.PASSED),
                history(registration(21, offering.get(2)), GradeResult.PASSED)));
        enrolled.removeLast();
        enrolled.removeLast();
        assertThrows(IllegalStateException.class, () -> service.cancel(1L));
        verify(registrations, never()).save(any());
    }

    @Test
    void cancellationPreservesSemesterPriorityWithoutChangingRejectedSelectionsOrGrades() {
        context();
        addOffering(4, 2);
        curriculum.get(1).setSemester(2);
        curriculum.get(2).setSemester(2);
        for (int index = 0; index < 4; index++) {
            enrolled.add(registration(index + 1, offering.get(index)));
        }
        arrangeCancellation(enrolled.getFirst());
        assertThrows(IllegalStateException.class, () -> service.cancel(1L));
        assertTrue(enrolled.stream().allMatch(item -> item.getStatus() == RegistrationStatus.ACTIVE));
        assertNull(enrolled.getFirst().getCancelledAt());
        verify(registrations, never()).save(any());
        verifyNoInteractions(grades);

        arrangeCancellation(enrolled.getLast());
        saveRegistrations();
        assertEquals(RegistrationStatus.CANCELLED, service.cancel(4L).status());
        assertEquals(RegistrationStatus.ACTIVE, enrolled.getFirst().getStatus());
    }

    @Test
    void cancellationDoesNotRevalidateExistingScheduleOrPrerequisiteProblems() {
        context();
        addOffering(4, 1);
        for (int index = 0; index < 4; index++) {
            enrolled.add(registration(index + 1, offering.get(index)));
        }
        offering.get(1).getScheduleBlocks().getFirst().setModule(1);
        CoursePrerequisite link = new CoursePrerequisite();
        link.setCourse(curriculum.get(2));
        link.setPrerequisite(curriculum.getFirst());
        when(prerequisites.findByCourseId(3L)).thenReturn(List.of(link));
        arrangeCancellation(enrolled.getLast());
        saveRegistrations();
        assertEquals(RegistrationStatus.CANCELLED, service.cancel(4L).status());
        assertTrue(enrolled.subList(0, 3).stream().allMatch(item -> item.getStatus() == RegistrationStatus.ACTIVE));
    }

    @Test
    void reactivatesACancelledRegistrationInsteadOfCreatingADuplicateRow() {
        context();
        CourseRegistration cancelled = registration(1, offering.get(0));
        cancelled.setStatus(RegistrationStatus.CANCELLED);
        cancelled.setCancelledAt(java.time.Instant.now());
        enrolled.add(cancelled);
        when(registrations.findByStudentIdAndSectionId(1L, 1L)).thenReturn(Optional.of(cancelled));
        saveRegistrations();
        assertEquals(3, service.register(1L, 1L, select(1L, 2L, 3L)).size());
        assertEquals(RegistrationStatus.ACTIVE, cancelled.getStatus());
        assertNull(cancelled.getCancelledAt());
    }

    @Test
    void reportsInvalidIdsMissingResourcesAndConcurrentCareerChanges() {
        assertThrows(IllegalArgumentException.class, () -> service.getByStudent(null));
        assertThrows(EntityNotFoundException.class, () -> service.getByStudent(99L));
        assertThrows(IllegalArgumentException.class, () -> service.getBySection(null));
        assertThrows(EntityNotFoundException.class, () -> service.getBySection(99L));
        assertThrows(IllegalArgumentException.class, () -> service.cancel(null));
        assertThrows(EntityNotFoundException.class, () -> service.cancel(99L));
        assertThrows(IllegalArgumentException.class, () -> service.register(null, 1L, select(1L)));
        assertThrows(IllegalArgumentException.class, () -> service.register(1L, null, select(1L)));
        context();
        assertThrows(EntityNotFoundException.class, () -> service.register(1L, 1L, select(99L)));
        assertThrows(EntityNotFoundException.class, () -> service.getAvailableSections(1L, 99L));
        period.setStatus(PeriodStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> service.getAvailableSections(1L, 1L));
        period.setStatus(PeriodStatus.OPEN);
        student.getStudyPlan().setDegreeProgram(identified(program(), 2));
        assertThrows(IllegalStateException.class, () -> service.register(1L, 1L, select(1L)));
    }
}
