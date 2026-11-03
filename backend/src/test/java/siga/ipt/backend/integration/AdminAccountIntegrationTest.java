package siga.ipt.backend.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import siga.ipt.backend.services.KeycloakRegistrationService;

class AdminAccountIntegrationTest extends AcademicIntegrationSupport {
    @MockitoBean KeycloakRegistrationService keycloak;

    private String body(String role) {
        long program = db.queryForObject("select degree_program_id from study_plans where id=?", Long.class, planId);
        return """
                {"username":"new-user","password":"test-password-123","run":"12345678-5",
                 "fullName":"New User","email":"new@example.test","role":"%s",
                 "degreeProgramId":%d,"professionalTitle":"Engineer","academicDegree":"Bachelor"}
                """.formatted(role, program);
    }

    @Test
    void adminCreatesStudentWithCurrentPlanAndRegularStatus() throws Exception {
        when(keycloak.createAccount(any())).thenReturn("new-student");
        mvc.perform(as(post("/api/users"), "admin", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(body("STUDENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keycloakSubject").value("new-student"))
                .andExpect(jsonPath("$.studentId").isNumber());
        org.junit.jupiter.api.Assertions.assertEquals("REGULAR", value(
                "select academic_status from students s join app_users u on u.id=s.user_id where u.keycloak_subject='new-student'"));
        org.junit.jupiter.api.Assertions.assertEquals(planId, db.queryForObject(
                "select study_plan_id from students s join app_users u on u.id=s.user_id where u.keycloak_subject='new-student'", Long.class));
        verify(keycloak).enableAccount("new-student");
    }

    @Test
    void adminCreatesActiveTeacherWhilePublicRegistrationIsDisabled() throws Exception {
        when(keycloak.createAccount(any())).thenReturn("new-teacher");
        mvc.perform(as(post("/api/users"), "admin", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(body("TEACHER")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.teacherId").isNumber());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", value(
                "select status from teachers t join app_users u on u.id=t.user_id where u.keycloak_subject='new-teacher'"));
        verify(keycloak).enableAccount("new-teacher");
    }

    @Test
    void rejectsAnonymousStudentsTeachersAndPublicRegistration() throws Exception {
        mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body("STUDENT")))
                .andExpect(status().isUnauthorized());
        for (String role : new String[]{"STUDENT", "TEACHER"}) {
            mvc.perform(as(post("/api/users"), role.toLowerCase(), role)
                            .contentType(MediaType.APPLICATION_JSON).content(body("TEACHER")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body("STUDENT")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(keycloak);
    }

    @Test
    void duplicateAcademicIdentityRollsBackTheKeycloakAccount() throws Exception {
        when(keycloak.createAccount(any())).thenReturn("duplicate-student");
        String duplicate = body("STUDENT").replace("12345678-5", "10000001-1");
        mvc.perform(as(post("/api/users"), "admin", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isBadRequest());
        verify(keycloak).rollbackAccount("duplicate-student");
        verify(keycloak, never()).enableAccount(any());
    }
}
