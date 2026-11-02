package siga.ipt.backend.integration;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Real controllers, security filters, services, transactions and PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
@Import(AcademicIntegrationSupport.TestTokens.class)
public abstract class AcademicIntegrationSupport {
    private static final String ISSUER = "https://integration.example.test/realms/siga";
    private static final KeyPair KEYS = keys();
    private static final JwtEncoder ENCODER = new NimbusJwtEncoder(new ImmutableJWKSet<>(
            new JWKSet(new RSAKey.Builder((RSAPublicKey) KEYS.getPublic())
                    .privateKey((RSAPrivateKey) KEYS.getPrivate()).keyID("integration-only").build())));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    long planId;
    long studentId;
    long teacherId;
    long periodId;
    long[] courses = new long[4];
    long[] sections = new long[4];

    @DynamicPropertySource
    static void configuration(DynamicPropertyRegistry properties) {
        // Validate before Hibernate can initialize or drop any schema.
        String url = System.getenv("INTEGRATION_DB_URL");
        if (!"jdbc:postgresql://integration-postgres:5432/siga_integration".equals(url)) {
            throw new IllegalStateException("Integration tests require the standalone Docker test database");
        }
        String password = System.getenv("INTEGRATION_DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Missing test database password");
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> "siga_integration");
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> ISSUER + "/certs");
        properties.add("app.cors.allowed-origins", () -> "http://integration.example.test");
        properties.add("app.registration.enabled", () -> "false");
        properties.add("app.registration.teacher-enabled", () -> "false");
        properties.add("app.keycloak.server-url", () -> "http://unused.example.test");
        properties.add("app.keycloak.realm", () -> "siga");
        properties.add("app.keycloak.registration-client-id", () -> "unused-in-integration");
        properties.add("app.keycloak.registration-client-secret", () -> "unused-in-integration");
        properties.add("app.keycloak.request-timeout-seconds", () -> "1");
    }

    @BeforeEach
    void prepareDatabase() {
        if (!"siga_integration".equals(db.queryForObject("select current_database()", String.class))) {
            throw new IllegalStateException("Refusing to clear a non-test database");
        }
        db.execute("TRUNCATE app_users, degree_programs, academic_periods RESTART IDENTITY CASCADE");
        long program = insert("insert into degree_programs(code,name,description,duration_semesters,status) values ('TEST','Test program','Integration fixture',4,'ACTIVE') returning id");
        planId = insert("insert into study_plans(degree_program_id,plan_version,status) values (?,'2027.1','CURRENT') returning id", program);
        long studentUser = user("student", "10000001-1");
        long teacherUser = user("teacher", "10000002-2");
        user("admin", "10000003-3");
        studentId = insert("insert into students(user_id,study_plan_id,academic_status) values (?,?,'REGULAR') returning id", studentUser, planId);
        teacherId = insert("insert into teachers(user_id,professional_title,academic_degree,status) values (?,'Engineer','Bachelor','ACTIVE') returning id", teacherUser);
        periodId = insert("insert into academic_periods(academic_year,semester,status) values (2027,1,'OPEN') returning id");
        db.update("insert into enrollments(row_version,student_id,academic_period_id,enrolled_at) values (0,?,?,current_timestamp)", studentId, periodId);
        for (int i = 0; i < 4; i++) {
            courses[i] = insert("insert into courses(study_plan_id,code,name,semester,theory_hours,exercise_hours,laboratory_hours,sct) values (?,?,?,1,2,0,0,4) returning id", planId, "TEST" + i, "Course " + i);
            sections[i] = insert("insert into sections(course_id,academic_period_id,teacher_id,capacity) values (?,?,?,30) returning id", courses[i], periodId, teacherId);
            db.update("insert into section_schedule_blocks(row_version,section_id,day_of_week,module) values (0,?,'MONDAY',?)", sections[i], i + 1);
        }
    }

    long user(String subject, String run) {
        return insert("insert into app_users(keycloak_subject,run,full_name,email,access_suspended) values (?,?,?,?,false) returning id",
                subject, run, "Test " + subject, subject + "@example.test");
    }

    long insert(String sql, Object... args) {
        String versioned = sql.replaceFirst("\\(", "(row_version,").replaceFirst("values \\(", "values (0,");
        return db.queryForObject(versioned, Long.class, args);
    }
    long count(String table) { return db.queryForObject("select count(*) from " + table, Long.class); }
    String value(String sql, Object... args) { return db.queryForObject(sql, String.class, args); }

    long registration(int index) {
        return insert("insert into course_registrations(student_id,section_id,registered_at,status) values (?,?,current_timestamp,'ACTIVE') returning id", studentId, sections[index]);
    }

    void grade(long registration, String value) {
        db.update("insert into final_grades(row_version,registration_id,grade_value) values (0,?,cast(? as numeric))", registration, value);
    }

    void gradedRegistrations(int count, String value) {
        for (int i = 0; i < count; i++) grade(registration(i), value);
    }

    MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String subject, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).subject(subject)
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("realm_access", Map.of("roles", List.of(role))).build();
        String token = ENCODER.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId("integration-only").build(), claims)).getTokenValue();
        return request.header("Authorization", "Bearer " + token);
    }

    private static KeyPair keys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestTokens {
        @Bean
        JwtDecoder integrationJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        }
    }
}
