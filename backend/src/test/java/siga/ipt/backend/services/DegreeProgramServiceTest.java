package siga.ipt.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static siga.ipt.backend.services.ServiceFixtures.*;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.access.AccessDeniedException;
import siga.ipt.backend.dtos.DegreeProgramDto;
import siga.ipt.backend.entitys.DegreeProgram;
import siga.ipt.backend.entitys.enums.ActiveStatus;
import siga.ipt.backend.entitys.enums.PlanStatus;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.StudyPlanRepository;

class DegreeProgramServiceTest extends ServiceTestSupport {

    @Mock private DegreeProgramRepository repository;
    @Mock private StudyPlanRepository plans;
    @InjectMocks private DegreeProgramService service;

    @Test
    void exposesOnlyTheReducedRegistrationCatalogWithoutRequiringAuthentication() {
        when(repository.findRegistrationOptions(ActiveStatus.ACTIVE, PlanStatus.CURRENT)).thenReturn(List.of(program()));
        var result = service.getRegistrationOptions();
        assertEquals(1L, result.getFirst().id());
        assertEquals("TAP", result.getFirst().code());
        assertEquals("Software Development", result.getFirst().name());
        verifyNoInteractions(currentUser);
    }

    @Test
    void createsAndNormalizesFourSemesterActivePrograms() {
        when(repository.save(any())).thenAnswer(call -> identified(call.getArgument(0), 2));
        DegreeProgramDto result = service.create(data(" TAP ", " Software ", " Description "));
        assertEquals("TAP", result.code());
        assertEquals("Software", result.name());
        assertEquals("Description", result.description());
        assertEquals(4, result.durationSemesters());
        assertEquals(ActiveStatus.ACTIVE, result.status());
        verify(currentUser).requireRole("ADMIN");
    }

    @Test
    void readsUpdatesChangesStatusAndDeletesAnUnusedProgram() {
        DegreeProgram entity = program();
        when(repository.findAll()).thenReturn(List.of(entity));
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        assertEquals(1, service.getAll().size());
        assertEquals("TAP", service.getById(1L).code());
        assertEquals("NEW", service.update(1L, data("NEW", "New name", "New description")).code());
        assertEquals(ActiveStatus.INACTIVE, service.changeStatus(1L,
                new DegreeProgramDto(null, null, null, null, null, ActiveStatus.INACTIVE)).status());
        service.delete(1L);
        verify(repository).delete(entity);
    }

    @Test
    void rejectsDuplicateCodesAndProgramsWithPlans() {
        when(repository.existsByCode("TAP")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(data("TAP", "Name", "Description")));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(program()));
        when(repository.existsByCodeAndIdNot("TAP", 1L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.update(1L, data("TAP", "Name", "Description")));
        when(plans.existsByDegreeProgramId(1L)).thenReturn(true);
        assertThrows(IllegalStateException.class, () -> service.delete(1L));
        verify(repository, never()).delete(any());
    }

    @Test
    void rejectsMissingIdsStatusesAndUnknownPrograms() {
        assertThrows(IllegalArgumentException.class, () -> service.getById(null));
        assertThrows(EntityNotFoundException.class, () -> service.getById(99L));
        assertThrows(IllegalArgumentException.class, () -> service.delete(null));
        assertThrows(EntityNotFoundException.class, () -> service.delete(99L));
        assertThrows(EntityNotFoundException.class, () -> service.update(99L, data("TAP", "N", "D")));
        assertThrows(IllegalArgumentException.class, () -> service.changeStatus(1L, null));
        assertThrows(EntityNotFoundException.class, () -> service.changeStatus(99L,
                new DegreeProgramDto(null, null, null, null, null, ActiveStatus.ACTIVE)));
    }

    @Test
    void authorizationFailurePreventsPersistence() {
        doThrow(new AccessDeniedException("ADMIN required")).when(currentUser).requireRole("ADMIN");
        assertThrows(AccessDeniedException.class, () -> service.create(data("TAP", "Name", "Description")));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @MethodSource("invalidData")
    void rejectsInvalidProgramData(DegreeProgramDto input) {
        assertThrows(IllegalArgumentException.class, () -> service.create(input));
        verify(repository, never()).save(any());
    }

    static Stream<DegreeProgramDto> invalidData() {
        return Stream.of(null, data(null, "N", "D"), data(" ", "N", "D"), data("x".repeat(31), "N", "D"),
                data("C", null, "D"), data("C", " ", "D"), data("C", "x".repeat(201), "D"),
                data("C", "N", null), data("C", "N", " "), data("C", "N", "x".repeat(2001)));
    }

    private static DegreeProgramDto data(String code, String name, String description) {
        return new DegreeProgramDto(null, code, name, description, 99, ActiveStatus.INACTIVE);
    }
}
