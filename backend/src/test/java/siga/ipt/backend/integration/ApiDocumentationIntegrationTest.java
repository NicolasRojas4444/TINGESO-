package siga.ipt.backend.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ApiDocumentationIntegrationTest extends AcademicIntegrationSupport {
    @Test
    void exposesApiContractsAndJwtAuthorizationAtTheCurrentOrigin() throws Exception {
        mvc.perform(get("/api/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("SIGA IPT REST API"))
                .andExpect(jsonPath("$.servers[0].url").value("/"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/students'].get.responses['200'].content.*.schema.items['$ref']")
                        .value(hasItem("#/components/schemas/StudentDto")))
                .andExpect(jsonPath("$.paths['/api/registrations'].post.requestBody").exists())
                .andExpect(jsonPath("$.paths['/api/final-grades/by-registration/{id}'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/academic-periods/{id}/close'].post.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/academic-periods/{id}/close'].post.responses['409'].content['application/problem+json'].example.code")
                        .value("FINAL_GRADES_MISSING"));
    }

    @Test
    void servesSwaggerUiAssetsAndRelativeConfigurationWithoutLogin() throws Exception {
        mvc.perform(get("/api/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/swagger-ui/index.html"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/api/swagger-ui/swagger-ui.css")).andExpect(status().isOk());
        mvc.perform(get("/api/v3/api-docs/swagger-config"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/api/v3/api-docs"));
    }

    @Test
    void documentsPublicOperationsWithoutRequiringAnAccessToken() throws Exception {
        mvc.perform(get("/api/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/public/degree-programs'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.security").isEmpty());
    }

    @Test
    void documentationDoesNotRemoveBusinessAuthenticationOrRoleChecks() throws Exception {
        mvc.perform(get("/api/students")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isUnauthorized());
        mvc.perform(as(get("/api/students"), "student", "STUDENT"))
                .andExpect(status().isForbidden());
        mvc.perform(as(get("/api/students"), "admin", "ADMIN"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
}
