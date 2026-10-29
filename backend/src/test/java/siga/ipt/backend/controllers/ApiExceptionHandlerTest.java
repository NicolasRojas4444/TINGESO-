package siga.ipt.backend.controllers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import siga.ipt.backend.exceptions.IncompleteGradesException;

class ApiExceptionHandlerTest {

    @Test
    void incompleteGradesHaveAStableCodeAndStructuredSectionIds() {
        var problem = new ApiExceptionHandler().incompleteGrades(new IncompleteGradesException(List.of(4L, 8L)));
        assertEquals(409, problem.getStatus());
        assertEquals("Final grades are missing", problem.getDetail());
        assertNotNull(problem.getProperties());
        assertEquals("FINAL_GRADES_MISSING", problem.getProperties().get("code"));
        assertEquals(List.of(4L, 8L), problem.getProperties().get("sectionIds"));
    }
}
