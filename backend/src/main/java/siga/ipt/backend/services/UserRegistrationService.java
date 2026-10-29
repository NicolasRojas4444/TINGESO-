package siga.ipt.backend.services;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import siga.ipt.backend.dtos.SelfRegistrationRole;
import siga.ipt.backend.dtos.StudentDto;
import siga.ipt.backend.dtos.TeacherDto;
import siga.ipt.backend.dtos.UserDto;
import siga.ipt.backend.dtos.UserRegistrationRequest;
import siga.ipt.backend.dtos.UserRegistrationResponse;

@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final KeycloakRegistrationService keycloak;
    private final StudentService students;
    private final TeacherService teachers;
    private final EntityManager entityManager;

    @Value("${app.registration.enabled}")
    private boolean enabled;

    @Value("${app.registration.teacher-enabled}")
    private boolean teacherEnabled;

    @Transactional
    public UserRegistrationResponse register(UserRegistrationRequest request) {
        if (!enabled) {
            throw new AccessDeniedException("Public registration is disabled");
        }
        validateRequest(request);
        if (request.role() == SelfRegistrationRole.TEACHER && !teacherEnabled) {
            throw new AccessDeniedException("Public teacher registration is disabled");
        }

        String subject = keycloak.createAccount(request);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    keycloak.rollbackAccount(subject);
                }
            }
        });

        UserDto user = new UserDto(null, subject, request.run(), request.fullName(), request.email());
        Long studentId = null;
        Long teacherId = null;

        if (request.role() == SelfRegistrationRole.STUDENT) {
            StudentDto student = students.createRegisteredProfile(request.degreeProgramId(),
                    new StudentDto(null, user, null, null, null));
            studentId = student.id();
        } else {
            TeacherDto teacher = teachers.createRegisteredProfile(new TeacherDto(
                    null, user, request.professionalTitle(), request.academicDegree(), null
            ));
            teacherId = teacher.id();
        }

        entityManager.flush();
        keycloak.enableAccount(subject);

        return new UserRegistrationResponse(subject, request.username().trim(), request.role(), studentId, teacherId);
    }

    private void validateRequest(UserRegistrationRequest request) {
        if (request == null || request.role() == null) {
            throw new IllegalArgumentException("Select STUDENT or TEACHER");
        }
        if (request.username() == null
                || !request.username().trim().matches("[A-Za-z0-9._-]{3,80}")) {
            throw new IllegalArgumentException("Username must contain 3 to 80 letters, digits, dots, underscores or hyphens");
        }
        if (request.password() == null || request.password().isBlank()
                || request.password().length() < 8 || request.password().length() > 128) {
            throw new IllegalArgumentException("Password must contain between 8 and 128 characters");
        }
        if (request.fullName() == null || request.fullName().isBlank()
                || request.fullName().trim().length() > 200) {
            throw new IllegalArgumentException("Full name is required and must not exceed 200 characters");
        }
        if (request.email() == null || request.email().trim().length() > 254
                || !request.email().trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("A valid email is required");
        }
        if (request.role() == SelfRegistrationRole.STUDENT && request.degreeProgramId() == null) {
            throw new IllegalArgumentException("A degree program is required for a student");
        }
        if (request.role() == SelfRegistrationRole.TEACHER
                && (request.professionalTitle() == null || request.professionalTitle().isBlank()
                    || request.academicDegree() == null || request.academicDegree().isBlank())) {
            throw new IllegalArgumentException("Professional title and academic degree are required for a teacher");
        }
    }
}
