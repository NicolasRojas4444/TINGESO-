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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import siga.ipt.backend.dtos.AcademicPeriodDto;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.repositories.AcademicPeriodRepository;

class AcademicPeriodServiceTest extends ServiceTestSupport {

    @Mock private AcademicPeriodRepository repository;
    @InjectMocks private AcademicPeriodService service;

    @Test
    void createsAnOpenPeriodIgnoringClientClosingData() {
        when(repository.save(any())).thenAnswer(call -> identified(call.getArgument(0), 2));
        AcademicPeriodDto result = service.create(new AcademicPeriodDto(null, 2027, 2, PeriodStatus.CLOSED, null, 8L));
        assertEquals(PeriodStatus.OPEN, result.status());
        assertNull(result.closedById());
        assertEquals(2, result.semester());
        verify(currentUser).requireRole("ADMIN");
    }

    @Test
    void listsAndReadsExistingPeriods() {
        when(repository.findAll()).thenReturn(List.of(period()));
        when(repository.findById(1L)).thenReturn(Optional.of(period()));
        assertEquals(1, service.getAll().size());
        assertEquals(2027, service.getById(1L).year());
    }

    @Test
    void rejectsDuplicatePeriodsAndMissingIds() {
        when(repository.existsByYearAndSemester(2027, 1)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(data(2027, 1)));
        assertThrows(IllegalArgumentException.class, () -> service.getById(null));
        assertThrows(EntityNotFoundException.class, () -> service.getById(99L));
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("invalidPeriods")
    void rejectsInvalidPeriodData(AcademicPeriodDto input) {
        assertThrows(IllegalArgumentException.class, () -> service.create(input));
        verify(repository, never()).save(any());
    }

    static Stream<Arguments> invalidPeriods() {
        return Stream.of(Arguments.of((Object) null), Arguments.of(data(null, 1)), Arguments.of(data(0, 1)),
                Arguments.of(data(-1, 1)), Arguments.of(data(2027, null)), Arguments.of(data(2027, 0)),
                Arguments.of(data(2027, 3)));
    }

    private static AcademicPeriodDto data(Integer year, Integer semester) {
        return new AcademicPeriodDto(null, year, semester, null, null, null);
    }
}
