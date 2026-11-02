package siga.ipt.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

class AcademicClosingIntegrationTest extends AcademicIntegrationSupport {
    @Test
    void consolidatesHistoryAndKeepsIncompleteCurriculumStudentRegular() throws Exception {
        gradedRegistrations(3, "5.0");
        mvc.perform(as(post("/api/academic-periods/{id}/close", periodId), "admin", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        assertThat(count("academic_records")).isEqualTo(3);
        assertThat(value("select academic_status from students where id=?", studentId)).isEqualTo("REGULAR");
        assertThat(value("select closed_at::text from academic_periods where id=?", periodId)).isNotBlank();
        assertThat(value("select course_name from academic_records order by id limit 1")).isEqualTo("Course 0");
        db.update("update courses set name='Updated catalog name' where id=?", courses[0]);
        assertThat(value("select course_name from academic_records order by id limit 1")).isEqualTo("Course 0");
    }

    @Test
    void graduatesStudentWhoPassesEntireCurriculum() throws Exception {
        gradedRegistrations(4, "4.0");
        mvc.perform(as(post("/api/academic-periods/{id}/close", periodId), "admin", "ADMIN"))
                .andExpect(status().isOk());
        assertThat(count("academic_records")).isEqualTo(4);
        assertThat(value("select academic_status from students where id=?", studentId)).isEqualTo("GRADUATED");
    }

    @Test
    void eliminatesStudentOnSecondFailureAndRetainsAcademicData() throws Exception {
        long previousPeriod = insert("insert into academic_periods(academic_year,semester,status,closed_at) values (2026,2,'CLOSED',current_timestamp) returning id");
        long previousSection = insert("insert into sections(course_id,academic_period_id,teacher_id,capacity) values (?,?,?,30) returning id", courses[0], previousPeriod, teacherId);
        long previousRegistration = insert("insert into course_registrations(student_id,section_id,registered_at,status) values (?,?,current_timestamp,'ACTIVE') returning id", studentId, previousSection);
        grade(previousRegistration, "3.0");
        insert("insert into academic_records(registration_id,course_code,course_name,curriculum_semester,theory_hours,exercise_hours,laboratory_hours,sct,final_grade,result,consolidated_at) values (?,'TEST0','Course 0',1,2,0,0,4,3.0,'FAILED',current_timestamp) returning id", previousRegistration);
        gradedRegistrations(3, "3.0");
        mvc.perform(as(post("/api/academic-periods/{id}/close", periodId), "admin", "ADMIN"))
                .andExpect(status().isOk());
        assertThat(value("select academic_status from students where id=?", studentId)).isEqualTo("ELIMINATED");
        assertThat(count("academic_records")).isEqualTo(4);
        assertThat(count("course_registrations")).isEqualTo(4);
        assertThat(count("final_grades")).isEqualTo(4);
    }

    @Test
    void rejectsMissingGradesAndReportsIncompleteSectionsWithoutPartialClosing() throws Exception {
        gradedRegistrations(2, "5.0");
        registration(2);
        mvc.perform(as(post("/api/academic-periods/{id}/close", periodId), "admin", "ADMIN"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FINAL_GRADES_MISSING"))
                .andExpect(jsonPath("$.sectionIds[0]").value(sections[2]))
                .andExpect(jsonPath("$.sectionIds.length()").value(1));
        assertThat(count("academic_records")).isZero();
        assertThat(value("select status from academic_periods where id=?", periodId)).isEqualTo("OPEN");
        assertThat(value("select closed_at::text from academic_periods where id=?", periodId)).isNull();
        assertThat(value("select academic_status from students where id=?", studentId)).isEqualTo("REGULAR");
    }

    @Test
    void rejectsClosingByTeacherWithoutChangingHistoryOrPeriod() throws Exception {
        gradedRegistrations(3, "5.0");
        mvc.perform(as(post("/api/academic-periods/{id}/close", periodId), "teacher", "TEACHER"))
                .andExpect(status().isForbidden());
        assertThat(count("academic_records")).isZero();
        assertThat(value("select status from academic_periods where id=?", periodId)).isEqualTo("OPEN");
    }

    @Test
    void rejectsRepeatedClosingWithoutDuplicatingHistory() throws Exception {
        gradedRegistrations(3, "5.0");
        mvc.perform(as(post("/api/academic-periods/{id}/close", periodId), "admin", "ADMIN"))
                .andExpect(status().isOk());
        String closedAt = value("select closed_at::text from academic_periods where id=?", periodId);
        mvc.perform(as(post("/api/academic-periods/{id}/close", periodId), "admin", "ADMIN"))
                .andExpect(status().isConflict());
        assertThat(count("academic_records")).isEqualTo(3);
        assertThat(value("select closed_at::text from academic_periods where id=?", periodId)).isEqualTo(closedAt);
    }
}
