package siga.ipt.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static siga.ipt.backend.services.ServiceFixtures.*;

import jakarta.persistence.EntityNotFoundException;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import siga.ipt.backend.dtos.SectionDto;
import siga.ipt.backend.dtos.SectionScheduleBlockDto;
import siga.ipt.backend.entitys.Course;
import siga.ipt.backend.entitys.Section;
import siga.ipt.backend.entitys.Teacher;
import siga.ipt.backend.entitys.enums.ActiveStatus;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.entitys.enums.RegistrationStatus;
import siga.ipt.backend.repositories.AcademicPeriodRepository;
import siga.ipt.backend.repositories.CourseRegistrationRepository;
import siga.ipt.backend.repositories.CourseRepository;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.SectionRepository;
import siga.ipt.backend.repositories.TeacherRepository;

class SectionServiceTest extends ServiceTestSupport {

    @Mock private SectionRepository sections;
    @Mock private CourseRepository courses;
    @Mock private DegreeProgramRepository programs;
    @Mock private AcademicPeriodRepository periods;
    @Mock private TeacherRepository teachers;
    @Mock private CourseRegistrationRepository registrations;
    @InjectMocks private SectionService service;

    private void offerContext(Course course, Teacher teacher) {
        when(courses.findDegreeProgramIdById(1L)).thenReturn(Optional.of(1L));
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(program()));
        when(courses.findById(1L)).thenReturn(Optional.of(course));
        when(periods.findByIdForUpdate(1L)).thenReturn(Optional.of(period()));
        when(teachers.findByIdForUpdate(1L)).thenReturn(Optional.of(teacher));
    }

    private Section existingSection() {
        Section section = section(1, course(1, 1));
        when(sections.findCourseIdById(1L)).thenReturn(Optional.of(1L));
        when(courses.findDegreeProgramIdById(1L)).thenReturn(Optional.of(1L));
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(program()));
        when(courses.findById(1L)).thenReturn(Optional.of(section.getCourse()));
        when(sections.findAcademicPeriodIdById(1L)).thenReturn(Optional.of(1L));
        when(periods.findByIdForUpdate(1L)).thenReturn(Optional.of(section.getAcademicPeriod()));
        when(sections.findById(1L)).thenReturn(Optional.of(section));
        return section;
    }

    @Test
    void createsASectionWithOwnedScheduleBlocksAndExistingActiveTeacher() {
        offerContext(course(1, 1), teacher());
        when(sections.save(any())).thenAnswer(call -> identified(call.getArgument(0), 1));
        SectionDto result = service.create(1L, 1L, 1L, valid());
        assertEquals(30, result.capacity());
        assertEquals(1L, result.teacherId());
        assertEquals(1, result.scheduleBlocks().size());
        verify(sections).save(org.mockito.ArgumentMatchers.argThat(section ->
                section.getScheduleBlocks().getFirst().getSection() == section));
    }

    @Test
    void exposesOnlyActiveEnrollmentCountsAndNonNegativeAvailableSeats() {
        Section section = section(1, course(1, 1));
        when(sections.findById(1L)).thenReturn(Optional.of(section));
        when(registrations.countBySectionIdAndStatus(1L, RegistrationStatus.ACTIVE)).thenReturn(12L);
        SectionDto response = service.getById(1L);
        assertEquals(12L, response.registeredCount());
        assertEquals(18L, response.availableSeats());
        when(registrations.countBySectionIdAndStatus(1L, RegistrationStatus.ACTIVE)).thenReturn(31L);
        assertEquals(0L, service.getById(1L).availableSeats());
    }

    @Test
    void listsSectionsAndFiltersPeriodResultsByAccess() {
        Section section = section(1, course(1, 1));
        when(sections.findAll()).thenReturn(List.of(section));
        when(sections.findById(1L)).thenReturn(Optional.of(section));
        when(periods.existsById(1L)).thenReturn(true);
        when(sections.findByAcademicPeriodId(1L)).thenReturn(List.of(section));
        when(currentUser.canReadSection(1L, "teacher-subject")).thenReturn(true);
        when(teachers.existsById(1L)).thenReturn(true);
        when(sections.findByTeacherId(1L)).thenReturn(List.of(section));
        assertEquals(1, service.getAll().size());
        assertEquals(1L, service.getById(1L).id());
        assertEquals(1, service.getByPeriod(1L).size());
        when(currentUser.canReadSection(1L, "teacher-subject")).thenReturn(false);
        assertTrue(service.getByPeriod(1L).isEmpty());
        assertEquals(1, service.getByTeacher(1L).size());
        verify(currentUser).requireSectionAccess(1L, "teacher-subject");
    }

    @Test
    void updatesCapacityAndSchedulesWithoutConflictingWithItsOwnExistingBlocks() {
        Section section = existingSection();
        when(teachers.findByIdForUpdate(1L)).thenReturn(Optional.of(teacher()));
        when(sections.findByTeacherIdAndAcademicPeriodId(1L, 1L)).thenReturn(List.of(section));
        when(sections.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(30, service.update(1L, 1L, valid()).capacity());
        verify(sections).flush();
        assertSame(section, section.getScheduleBlocks().getFirst().getSection());
    }

    @Test
    void rejectsCapacityBelowCurrentEnrollmentAndStudentScheduleConflicts() {
        existingSection();
        when(teachers.findByIdForUpdate(1L)).thenReturn(Optional.of(teacher()));
        when(registrations.countBySectionIdAndStatus(1L, RegistrationStatus.ACTIVE)).thenReturn(31L);
        assertThrows(IllegalStateException.class, () -> service.update(1L, 1L, valid()));
        when(registrations.countBySectionIdAndStatus(1L, RegistrationStatus.ACTIVE)).thenReturn(0L);
        Section other = section(2, course(2, 1));
        other.getScheduleBlocks().getFirst().setModule(1);
        when(registrations.findOtherSectionsForEnrolledStudents(1L, 1L, RegistrationStatus.ACTIVE)).thenReturn(List.of(other));
        assertThrows(IllegalStateException.class, () -> service.update(1L, 1L, valid()));
        verify(sections, never()).save(any());
    }

    @Test
    void rejectsDuplicateOfferingsInactiveTeachersAndOverlappingTeacherSchedules() {
        Teacher teacher = teacher();
        offerContext(course(1, 1), teacher);
        when(sections.existsByCourseIdAndAcademicPeriodId(1L, 1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, 1L, 1L, valid()));
        when(sections.existsByCourseIdAndAcademicPeriodId(1L, 1L)).thenReturn(false);
        teacher.setStatus(ActiveStatus.INACTIVE);
        assertThrows(IllegalStateException.class, () -> service.create(1L, 1L, 1L, valid()));
        teacher.setStatus(ActiveStatus.ACTIVE);
        Section other = section(2, course(2, 1));
        other.getScheduleBlocks().getFirst().setModule(1);
        when(sections.findByTeacherIdAndAcademicPeriodId(1L, 1L)).thenReturn(List.of(other));
        assertThrows(IllegalStateException.class, () -> service.create(1L, 1L, 1L, valid()));
    }

    @Test
    void allowsNonOverlappingTeacherAssignments() {
        offerContext(course(1, 1), teacher());
        when(sections.findByTeacherIdAndAcademicPeriodId(1L, 1L)).thenReturn(List.of(section(2, course(2, 1))));
        when(sections.save(any())).thenAnswer(call -> identified(call.getArgument(0), 1));
        assertNotNull(service.create(1L, 1L, 1L, valid()));
    }

    @Test
    void deletesOnlyUnregisteredSectionsInOpenPeriods() {
        Section section = existingSection();
        when(registrations.existsBySectionId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        when(registrations.existsBySectionId(1L)).thenReturn(false);
        service.delete(1L);
        verify(sections).delete(section);
        section.getAcademicPeriod().setStatus(PeriodStatus.CLOSED);
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
    }

    @ParameterizedTest
    @MethodSource("invalidSections")
    void rejectsInvalidCapacityAndSchedules(SectionDto data) {
        offerContext(course(1, 1), teacher());
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, 1L, 1L, data));
        verify(sections, never()).save(any());
    }

    static Stream<SectionDto> invalidSections() {
        SectionScheduleBlockDto monday = new SectionScheduleBlockDto(null, DayOfWeek.MONDAY, 1);
        return Stream.of(null, data(null,List.of(monday)), data(0,List.of(monday)), data(61,List.of(monday)),
                data(30,null), data(30,List.of()), data(30,Arrays.asList((SectionScheduleBlockDto) null)),
                data(30,List.of(new SectionScheduleBlockDto(null,null,1))),
                data(30,List.of(new SectionScheduleBlockDto(null,DayOfWeek.MONDAY,null))),
                data(30,List.of(new SectionScheduleBlockDto(null,DayOfWeek.SATURDAY,1))),
                data(30,List.of(new SectionScheduleBlockDto(null,DayOfWeek.MONDAY,0))),
                data(30,List.of(new SectionScheduleBlockDto(null,DayOfWeek.MONDAY,7))),
                data(30,List.of(monday,monday)),
                data(30,List.of(monday,new SectionScheduleBlockDto(null,DayOfWeek.TUESDAY,1))));
    }

    @Test
    void rejectsNullIdsAndMissingSectionsPeriodsTeachersAndCourses() {
        assertThrows(IllegalArgumentException.class, () -> service.getById(null));
        assertThrows(EntityNotFoundException.class, () -> service.getById(99L));
        assertThrows(IllegalArgumentException.class, () -> service.getByPeriod(null));
        assertThrows(EntityNotFoundException.class, () -> service.getByPeriod(99L));
        assertThrows(IllegalArgumentException.class, () -> service.getByTeacher(null));
        assertThrows(EntityNotFoundException.class, () -> service.getByTeacher(99L));
        assertThrows(EntityNotFoundException.class, () -> service.delete(99L));
        assertThrows(IllegalArgumentException.class, () -> service.create(null, 1L, 1L, valid()));
        assertThrows(EntityNotFoundException.class, () -> service.create(99L, 1L, 1L, valid()));
    }

    private static SectionDto valid() {
        return data(30, List.of(new SectionScheduleBlockDto(null, DayOfWeek.MONDAY, 1)));
    }

    private static SectionDto data(Integer capacity, List<SectionScheduleBlockDto> blocks) {
        return new SectionDto(null, null, null, null, null, null, capacity, null, null, blocks);
    }
}
