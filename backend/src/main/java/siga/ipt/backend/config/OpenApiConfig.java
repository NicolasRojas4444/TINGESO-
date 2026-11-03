package siga.ipt.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI academicApi() {
        return new OpenAPI()
                .info(new Info().title("SIGA IPT REST API").version("1.0")
                        .description("""
                                Academic administration, course registration, final grades and period closing.

                                Use **Authorize** with a Keycloak access token (paste the JWT without the
                                `Bearer` prefix). Roles and ownership are checked by the backend:
                                ADMIN administers academic data; TEACHER manages grades for assigned sections;
                                STUDENT accesses their own information and registrations.

                                Errors use `application/problem+json` with `status`, `title` and `detail`:
                                400 invalid input, 401 missing/invalid session, 403 forbidden operation,
                                404 resource not found, 409 business/data conflict, and 503 IAM unavailable.
                                A rejected period closing with missing grades also includes
                                `code=FINAL_GRADES_MISSING` and `sectionIds`.
                                """))
                // A relative origin works both locally and behind the HTTPS Nginx proxy.
                .servers(List.of(new Server().url("/").description("Current server")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                                .bearerFormat("JWT").description("Keycloak access token for the siga realm")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
