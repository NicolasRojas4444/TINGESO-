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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import siga.ipt.backend.dtos.StudyPlanDto;
import siga.ipt.backend.entitys.StudyPlan;
import siga.ipt.backend.entitys.enums.PlanStatus;
import siga.ipt.backend.repositories.CourseRepository;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.StudentRepository;
import siga.ipt.backend.repositories.StudyPlanRepository;

class StudyPlanServiceTest extends ServiceTestSupport {

    @Mock private StudyPlanRepository repository;
    @Mock private DegreeProgramRepository programs;
    @Mock private StudentRepository students;
    @Mock private CourseRepository courses;
    @InjectMocks private StudyPlanService service;

    private void lockExisting(StudyPlan entity) {
        when(repository.findDegreeProgramIdById(entity.getId())).thenReturn(Optional.of(1L));
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(entity.getDegreeProgram()));
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));
    }

    @Test
    void readsAllPlansAndPlansByProgram() {
        when(repository.findAll()).thenReturn(List.of(plan()));
        when(repository.findById(1L)).thenReturn(Optional.of(plan()));
        when(programs.existsById(1L)).thenReturn(true);
        when(repository.findByDegreeProgramId(1L)).thenReturn(List.of(plan()));
        assertEquals(1, service.getAll().size());
        assertEquals("2026.1", service.getById(1L).version());
        assertEquals(1, service.getByDegreeProgram(1L).size());
    }

    @Test
    void creatingCurrentPlanRetiresPreviousPlanWithoutReassigningStudents() {
        StudyPlan previous = plan();
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(program()));
        when(repository.save(any())).thenAnswer(call -> {
            StudyPlan saved = call.getArgument(0);
            return saved.getId() == null ? identified(saved, 2) : saved;
        });
        when(repository.findByDegreeProgramIdAndStatus(1L, PlanStatus.CURRENT)).thenReturn(List.of(previous));
        StudyPlanDto result = service.create(1L, data(" 2027.1 ", PlanStatus.CURRENT));
        assertEquals("2027.1", result.version());
        assertEquals(PlanStatus.CURRENT, result.status());
        assertEquals(PlanStatus.NON_CURRENT, previous.getStatus());
        verifyNoInteractions(students);
    }

    @Test
    void createsANonCurrentPlanByDefault() {
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(program()));
        when(repository.save(any())).thenAnswer(call -> identified(call.getArgument(0), 2));
        assertEquals(PlanStatus.NON_CURRENT, service.create(1L, data("V2", null)).status());
    }

    @Test
    void updatesAndSetsCurrentAfterLockingTheProgram() {
        StudyPlan entity = plan();
        lockExisting(entity);
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(repository.findByDegreeProgramIdAndStatus(1L, PlanStatus.CURRENT)).thenReturn(List.of(entity));
        assertEquals("V2", service.update(1L, data("V2", null)).version());
        InOrder order = inOrder(programs, repository);
        order.verify(repository).findDegreeProgramIdById(1L);
        order.verify(programs).findByIdForUpdate(1L);
        order.verify(repository).findById(1L);
        assertEquals(PlanStatus.CURRENT, service.setCurrent(1L).status());
    }

    @Test
    void rejectsDuplicatesOnCreationAndUpdate() {
        when(programs.findByIdForUpdate(1L)).thenReturn(Optional.of(program()));
        when(repository.existsByDegreeProgramIdAndVersion(1L, "V2")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, data("V2", null)));
        lockExisting(plan());
        when(repository.existsByDegreeProgramIdAndVersionAndIdNot(1L, "V2", 1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.update(1L, data("V2", null)));
    }

    @Test
    void protectsPlansWithStudentsOrCoursesAndDeletesUnusedPlans() {
        StudyPlan entity = plan();
        lockExisting(entity);
        when(students.existsByStudyPlanId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        when(students.existsByStudyPlanId(1L)).thenReturn(false);
        when(courses.existsByStudyPlanId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        when(courses.existsByStudyPlanId(1L)).thenReturn(false);
        service.delete(1L);
        verify(repository).delete(entity);
    }

    @Test
    void rejectsNullAndUnknownResources() {
        assertThrows(IllegalArgumentException.class, () -> service.getById(null));
        assertThrows(EntityNotFoundException.class, () -> service.getById(99L));
        assertThrows(IllegalArgumentException.class, () -> service.getByDegreeProgram(null));
        assertThrows(EntityNotFoundException.class, () -> service.getByDegreeProgram(99L));
        assertThrows(IllegalArgumentException.class, () -> service.setCurrent(null));
        assertThrows(EntityNotFoundException.class, () -> service.setCurrent(99L));
        assertThrows(IllegalArgumentException.class, () -> service.create(null, data("V2", null)));
        assertThrows(EntityNotFoundException.class, () -> service.create(99L, data("V2", null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, null));
        when(repository.findDegreeProgramIdById(8L)).thenReturn(Optional.of(99L));
        assertThrows(EntityNotFoundException.class, () -> service.delete(8L));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "1234567890123456789012345678901"})
    void rejectsInvalidVersions(String version) {
        assertThrows(IllegalArgumentException.class, () -> service.create(1L, data(version, null)));
        verify(repository, never()).save(any());
    }

    private StudyPlanDto data(String version, PlanStatus status) {
        return new StudyPlanDto(null, 1L, version, status);
    }
}
