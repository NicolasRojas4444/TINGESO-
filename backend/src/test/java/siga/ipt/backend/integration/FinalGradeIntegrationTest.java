package siga.ipt.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class FinalGradeIntegrationTest extends AcademicIntegrationSupport {
    @Test
    void responsibleTeacherRecordsPassingBoundaryGrade() throws Exception {
        long id = registration(0);
        mvc.perform(as(post("/api/final-grades/by-registration/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":4.0}"), "teacher", "TEACHER"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.result").value("PASSED"));
        assertThat(value("select grade_value::text from final_grades where registration_id=?", id)).isEqualTo("4.0");
    }

    @Test
    void responsibleTeacherRecordsFailingGrade() throws Exception {
        long id = registration(0);
        mvc.perform(as(post("/api/final-grades/by-registration/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":3.9}"), "teacher", "TEACHER"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.result").value("FAILED"));
        assertThat(value("select grade_value::text from final_grades where registration_id=?", id)).isEqualTo("3.9");
    }

    @Test
    void updatesExistingGradeWhilePeriodIsOpen() throws Exception {
        long id = registration(0);
        grade(id, "3.0");
        mvc.perform(as(put("/api/final-grades/by-registration/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":7.0}"), "teacher", "TEACHER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("PASSED"));
        assertThat(count("final_grades")).isEqualTo(1);
        assertThat(value("select grade_value::text from final_grades where registration_id=?", id)).isEqualTo("7.0");
    }

    @Test
    void rejectsGradeFromAnotherTeacher() throws Exception {
        long id = registration(0);
        mvc.perform(as(post("/api/final-grades/by-registration/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":5.0}"), "other-teacher", "TEACHER"))
                .andExpect(status().isForbidden());
        assertThat(count("final_grades")).isZero();
    }

    @Test
    void rejectsOutOfRangeGradeWithoutWriting() throws Exception {
        long id = registration(0);
        mvc.perform(as(post("/api/final-grades/by-registration/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":7.1}"), "teacher", "TEACHER"))
                .andExpect(status().isBadRequest());
        assertThat(count("final_grades")).isZero();
    }

    @Test
    void rejectsGradeUpdateAfterClosingAndPreservesValue() throws Exception {
        long id = registration(0);
        grade(id, "5.0");
        mvc.perform(as(post("/api/academic-periods/{id}/close", periodId), "admin", "ADMIN"))
                .andExpect(status().isOk());
        mvc.perform(as(put("/api/final-grades/by-registration/{id}", id)
                .contentType(MediaType.APPLICATION_JSON).content("{\"value\":6.0}"), "teacher", "TEACHER"))
                .andExpect(status().isConflict());
        assertThat(value("select grade_value::text from final_grades where registration_id=?", id)).isEqualTo("5.0");
        assertThat(value("select final_grade::text from academic_records where registration_id=?", id)).isEqualTo("5.0");
    }
}
