package siga.ipt.backend.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static siga.ipt.backend.services.ServiceFixtures.*;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.access.AccessDeniedException;
import siga.ipt.backend.entitys.enums.GradeResult;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.repositories.AcademicRecordRepository;
import siga.ipt.backend.repositories.StudentRepository;

class AcademicHistoryServiceTest extends ServiceTestSupport {

    @Mock private AcademicRecordRepository records;
    @Mock private StudentRepository students;
    @InjectMocks private AcademicHistoryService service;

    @Test
    void returnsOnlyConsolidatedHistoryForAnAuthorizedStudent() {
        when(students.existsById(1L)).thenReturn(true);
        when(records.findHistoryByStudentId(1L, PeriodStatus.CLOSED)).thenReturn(List.of(
                history(registration(1, section(1, course(1, 1))), GradeResult.PASSED)));
        assertEquals(GradeResult.PASSED, service.getByStudent(1L).getFirst().result());
        verify(currentUser).requireStudentAccess(1L);
    }

    @Test
    void rejectsMissingStudentIdsAndUnknownStudents() {
        assertThrows(IllegalArgumentException.class, () -> service.getByStudent(null));
        assertThrows(EntityNotFoundException.class, () -> service.getByStudent(99L));
        verifyNoInteractions(records);
    }

    @Test
    void rejectsUnauthorizedHistoryBeforeQueryingIt() {
        doThrow(new AccessDeniedException("Not your history")).when(currentUser).requireStudentAccess(2L);
        assertThrows(AccessDeniedException.class, () -> service.getByStudent(2L));
        verifyNoInteractions(records, students);
    }
}
