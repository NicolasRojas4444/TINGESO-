package siga.ipt.backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class RegistrationIntegrationTest extends AcademicIntegrationSupport {
    private MockHttpServletRequestBuilder selection(int... indexes) {
        String body = java.util.Arrays.stream(indexes)
                .mapToObj(i -> "{\"sectionId\":" + sections[i] + "}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        return post("/api/registrations").param("studentId", "" + studentId)
                .param("periodId", "" + periodId).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void registersThreeCoursesAndOccupiesSeats() throws Exception {
        mvc.perform(as(selection(0, 1, 2), "student", "STUDENT"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(3));
        assertThat(count("course_registrations")).isEqualTo(3);
        mvc.perform(as(get("/api/sections/{id}", sections[0]), "student", "STUDENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableSeats").value(29));
    }

    @Test
    void replacesSelectionAtomicallyPreservingRetainedGrades() throws Exception {
        long retained = registration(0);
        grade(retained, "5.0");
        registration(1);
        long removed = registration(2);
        grade(removed, "3.0");
        mvc.perform(as(put("/api/registrations").param("studentId", "" + studentId)
                .param("periodId", "" + periodId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectionIds\":[" + sections[0] + "," + sections[1] + "," + sections[3] + "]}"), "student", "STUDENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        assertThat(value("select status from course_registrations where id=?", removed)).isEqualTo("CANCELLED");
        assertThat(count("final_grades")).isEqualTo(1);
        assertThat(value("select grade_value::text from final_grades where registration_id=?", retained)).isEqualTo("5.0");
        assertThat(db.queryForObject("select count(*) from course_registrations where status='ACTIVE'", Long.class)).isEqualTo(3);
    }

    @Test
    void cancelsFourthCourseAndReleasesSeatAndProvisionalGrade() throws Exception {
        for (int i = 0; i < 3; i++) registration(i);
        long removed = registration(3);
        grade(removed, "4.0");
        mvc.perform(as(delete("/api/registrations/{id}", removed), "student", "STUDENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(count("final_grades")).isZero();
        assertThat(value("select cancelled_at::text from course_registrations where id=?", removed)).isNotBlank();
        mvc.perform(as(get("/api/sections/{id}", sections[3]), "student", "STUDENT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.availableSeats").value(30));
    }

    @Test
    void rejectsMissingEnrollmentWithoutWritingRegistrations() throws Exception {
        db.update("delete from enrollments");
        mvc.perform(as(selection(0, 1, 2), "student", "STUDENT"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("The student is not enrolled in this academic period"));
        assertThat(count("course_registrations")).isZero();
    }

    @Test
    void rejectsFullSectionWithoutPartialWrites() throws Exception {
        long otherUser = user("other-student", "10000004-4");
        long other = insert("insert into students(user_id,study_plan_id,academic_status) values (?,?,'REGULAR') returning id", otherUser, planId);
        db.update("update sections set capacity=1 where id=?", sections[2]);
        insert("insert into course_registrations(student_id,section_id,registered_at,status) values (?,?,current_timestamp,'ACTIVE') returning id", other, sections[2]);
        mvc.perform(as(selection(0, 1, 2), "student", "STUDENT"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Section has no available seats: " + sections[2]));
        assertThat(count("course_registrations")).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from course_registrations where student_id=?", Long.class, studentId)).isZero();
    }

    @Test
    void rejectsOverlappingReplacementPreservingOriginalSelectionAndGrades() throws Exception {
        for (int i = 0; i < 3; i++) grade(registration(i), "5.0");
        db.update("update section_schedule_blocks set module=1 where section_id=?", sections[3]);
        mvc.perform(as(put("/api/registrations").param("studentId", "" + studentId)
                .param("periodId", "" + periodId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sectionIds\":[" + sections[0] + "," + sections[1] + "," + sections[3] + "]}"), "student", "STUDENT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Selected sections have conflicting schedules"));
        assertThat(count("course_registrations")).isEqualTo(3);
        assertThat(count("final_grades")).isEqualTo(3);
        assertThat(db.queryForObject("select count(*) from course_registrations where status='CANCELLED'", Long.class)).isZero();
    }
}
