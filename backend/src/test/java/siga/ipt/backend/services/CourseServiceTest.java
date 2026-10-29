package siga.ipt.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static siga.ipt.backend.services.ServiceFixtures.*;

import jakarta.persistence.EntityNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import siga.ipt.backend.dtos.CourseDto;
import siga.ipt.backend.entitys.Course;
import siga.ipt.backend.entitys.CoursePrerequisite;
import siga.ipt.backend.entitys.Section;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.repositories.AcademicRecordRepository;
import siga.ipt.backend.repositories.CoursePrerequisiteRepository;
import siga.ipt.backend.repositories.CourseRepository;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.SectionRepository;
import siga.ipt.backend.repositories.StudyPlanRepository;

class CourseServiceTest extends ServiceTestSupport {

    @Mock private CourseRepository courses;
    @Mock private StudyPlanRepository plans;
    @Mock private DegreeProgramRepository programs;
    @Mock private CoursePrerequisiteRepository prerequisites;
    @Mock private SectionRepository sections;
    @Mock private AcademicRecordRepository history;
    @InjectMocks private CourseService service;

    private Course lockedCourse() {
        Course entity = course(1, 2);
        when(courses.findDegreeProgramIdById(1L)).thenReturn(Optional.of(1L));
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(program()));
        when(courses.findById(1L)).thenReturn(Optional.of(entity));
        return entity;
    }

    @Test
    void createsCoursesWithNormalizedCodeAndName() {
        when(plans.findById(1L)).thenReturn(Optional.of(plan()));
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(program()));
        when(courses.save(any())).thenAnswer(call -> identified(call.getArgument(0), 1));
        CourseDto result = service.create(1L, data(" TAP1 ", " Programming ", 1, 2, 0, 0, 7));
        assertEquals("TAP1", result.code());
        assertEquals("Programming", result.name());
        assertEquals(7, result.sct());
        verify(currentUser).requireRole("ADMIN");
    }

    @Test
    void readsCoursesByPlanAndListsTheirPrerequisites() {
        Course entity = course(1, 2);
        Course prerequisite = course(2, 1);
        when(courses.findAll()).thenReturn(List.of(entity));
        when(courses.findById(1L)).thenReturn(Optional.of(entity));
        when(plans.existsById(1L)).thenReturn(true);
        when(courses.findByStudyPlanIdOrderBySemesterAscCodeAsc(1L)).thenReturn(List.of(entity));
        when(prerequisites.findByCourseId(1L)).thenReturn(List.of(link(entity, prerequisite)));
        assertEquals(1, service.getAll().size());
        assertEquals("TAP1", service.getById(1L).code());
        assertEquals(1, service.getByStudyPlan(1L).size());
        assertEquals(2L, service.getPrerequisites(1L).getFirst().id());
    }

    @Test
    void updatesEditableCourseDataWhileKeepingItsIdentityAndPlan() {
        Course entity = lockedCourse();
        when(courses.findByStudyPlanIdAndCode(1L, "TAP1")).thenReturn(Optional.of(entity));
        when(courses.save(any())).thenAnswer(call -> call.getArgument(0));
        CourseDto result = service.update(1L, data("TAP1", "Updated", 2, 2, 0, 0, 5));
        assertEquals("Updated", result.name());
        assertEquals(5, result.sct());
        assertEquals(1L, result.studyPlanId());
    }

    @Test
    void rejectsDuplicateCodesWithinThePlan() {
        when(plans.findById(1L)).thenReturn(Optional.of(plan()));
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(program()));
        when(courses.findByStudyPlanIdAndCode(1L, "TAP1")).thenReturn(Optional.of(course(8, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, valid()));
        verify(courses, never()).save(any());
    }

    @Test
    void replacesPrerequisitesWithEarlierCoursesFromTheSamePlan() {
        Course entity = lockedCourse();
        Course earlier = course(2, 1);
        when(courses.findById(2L)).thenReturn(Optional.of(earlier));
        List<CoursePrerequisite> previous = List.of(link(entity, course(3, 1)));
        when(prerequisites.findByCourseId(1L)).thenReturn(previous);
        assertEquals(2L, service.setPrerequisites(1L, List.of(2L)).getFirst().id());
        verify(prerequisites).deleteAll(previous);
        verify(prerequisites).flush();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CoursePrerequisite>> captured = ArgumentCaptor.forClass(List.class);
        verify(prerequisites).saveAll(captured.capture());
        assertSame(entity, captured.getValue().getFirst().getCourse());
        assertSame(earlier, captured.getValue().getFirst().getPrerequisite());
    }

    @Test
    void rejectsTooManyDuplicatedSelfForeignAndNonEarlierPrerequisites() {
        lockedCourse();
        assertThrows(IllegalArgumentException.class, () -> service.setPrerequisites(1L, null));
        assertThrows(IllegalArgumentException.class, () -> service.setPrerequisites(1L, List.of(2L, 3L, 4L, 5L)));
        assertThrows(IllegalArgumentException.class, () -> service.setPrerequisites(1L, List.of(2L, 2L)));
        assertThrows(IllegalArgumentException.class, () -> service.setPrerequisites(1L, List.of(1L)));
        assertThrows(IllegalArgumentException.class, () -> service.setPrerequisites(1L, Arrays.asList((Long) null)));
        Course other = course(2, 1);
        other.setStudyPlan(identified(plan(), 2));
        when(courses.findById(2L)).thenReturn(Optional.of(other));
        assertThrows(IllegalArgumentException.class, () -> service.setPrerequisites(1L, List.of(2L)));
        other.setStudyPlan(plan());
        other.setSemester(2);
        assertThrows(IllegalArgumentException.class, () -> service.setPrerequisites(1L, List.of(2L)));
    }

    @Test
    void semesterChangesMustPreserveBothPrerequisiteDirections() {
        Course entity = lockedCourse();
        when(prerequisites.findByCourseId(1L)).thenReturn(List.of(link(entity, course(2, 1))));
        assertThrows(IllegalArgumentException.class, () -> service.update(1L, data("TAP1", "Name", 1, 2, 0, 0, 4)));
        when(prerequisites.findByPrerequisiteId(1L)).thenReturn(List.of(link(course(3, 3), entity)));
        assertThrows(IllegalArgumentException.class, () -> service.update(1L, data("TAP1", "Name", 3, 2, 0, 0, 4)));
        when(courses.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(2, service.update(1L, valid()).semester());
    }

    @Test
    void telChangesMustMatchTheSchedulesOfOpenSections() {
        Course entity = lockedCourse();
        Section section = section(1, entity);
        when(sections.findByCourseIdAndAcademicPeriodStatus(1L, PeriodStatus.OPEN)).thenReturn(List.of(section));
        CourseDto increased = data("TAP1", "Name", 2, 4, 0, 0, 4);
        assertThrows(IllegalStateException.class, () -> service.update(1L, increased));
        section.getScheduleBlocks().add(block(java.time.DayOfWeek.TUESDAY, 1));
        when(courses.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(4, service.update(1L, increased).theoryHours());
    }

    @Test
    void preventsDeletionWithHistorySectionsOrDependentCourses() {
        Course entity = lockedCourse();
        when(history.existsByRegistrationSectionCourseId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        when(history.existsByRegistrationSectionCourseId(1L)).thenReturn(false);
        when(sections.existsByCourseId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        when(sections.existsByCourseId(1L)).thenReturn(false);
        when(prerequisites.findByPrerequisiteId(1L)).thenReturn(List.of(link(course(2, 3), entity)));
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        when(prerequisites.findByPrerequisiteId(1L)).thenReturn(List.of());
        service.delete(1L);
        verify(courses).delete(entity);
        verify(prerequisites).deleteAll(List.of());
    }

    @Test
    void rejectsMissingIdsPlansCoursesAndPrograms() {
        assertThrows(IllegalArgumentException.class, () -> service.getById(null));
        assertThrows(EntityNotFoundException.class, () -> service.getById(99L));
        assertThrows(IllegalArgumentException.class, () -> service.getByStudyPlan(null));
        assertThrows(EntityNotFoundException.class, () -> service.getByStudyPlan(99L));
        assertThrows(IllegalArgumentException.class, () -> service.create(null, valid()));
        assertThrows(EntityNotFoundException.class, () -> service.create(99L, valid()));
        assertThrows(EntityNotFoundException.class, () -> service.delete(99L));
        when(courses.findDegreeProgramIdById(1L)).thenReturn(Optional.of(99L));
        assertThrows(EntityNotFoundException.class, () -> service.delete(1L));
    }

    @ParameterizedTest
    @MethodSource("invalidCourses")
    void rejectsInvalidCurriculumData(CourseDto input) {
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, input));
        verify(courses, never()).save(any());
    }

    static Stream<CourseDto> invalidCourses() {
        return Stream.of(null, data(null,"N",1,2,0,0,4), data(" ","N",1,2,0,0,4),
                data("x".repeat(31),"N",1,2,0,0,4), data("C",null,1,2,0,0,4),
                data("C"," ",1,2,0,0,4), data("C","x".repeat(201),1,2,0,0,4),
                data("C","N",null,2,0,0,4), data("C","N",0,2,0,0,4), data("C","N",5,2,0,0,4),
                data("C","N",1,2,0,0,null), data("C","N",1,2,0,0,0), data("C","N",1,2,0,0,8),
                data("C","N",1,null,0,0,4), data("C","N",1,-1,0,0,4),
                data("C","N",1,2,-1,0,4), data("C","N",1,2,0,-1,4), data("C","N",1,4,2,2,4));
    }

    private static CourseDto valid() { return data("TAP1", "Name", 2, 2, 0, 0, 4); }

    private static CourseDto data(String code, String name, Integer semester, Integer theory,
                                  Integer exercises, Integer laboratory, Integer sct) {
        return new CourseDto(null, 1L, code, name, semester, theory, exercises, laboratory, sct);
    }

    private static CoursePrerequisite link(Course course, Course prerequisite) {
        CoursePrerequisite link = new CoursePrerequisite();
        link.setCourse(course);
        link.setPrerequisite(prerequisite);
        return link;
    }
}
