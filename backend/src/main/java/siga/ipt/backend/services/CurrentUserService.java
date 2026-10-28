package siga.ipt.backend.services;

import jakarta.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import siga.ipt.backend.repositories.StudentRepository;
import siga.ipt.backend.repositories.TeacherRepository;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    public String subject() {
        return token().getSubject();
    }

    public boolean hasRole(String role) {
        Jwt jwt = token();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()))) {
            return true;
        }
        Object realmAccess = jwt.getClaim("realm_access");
        return realmAccess instanceof Map<?, ?> realm
                && realm.get("roles") instanceof Collection<?> roles
                && roles.contains(role);
    }

    public void requireRole(String role) {
        if (!hasRole(role)) {
            throw new AccessDeniedException("The " + role + " role is required");
        }
    }

    public void requireAcademicUser() {
        if (!hasRole("ADMIN") && !hasRole("TEACHER") && !hasRole("STUDENT")) {
            throw new AccessDeniedException("An academic role is required");
        }
    }

    public void requireStudentAccess(Long studentId) {
        if (hasRole("ADMIN")) {
            return;
        }
        requireOwnStudent(studentId);
    }

    public void requireOwnStudent(Long studentId) {
        requireRole("STUDENT");
        if (studentId == null || !studentId.equals(studentId())) {
            throw new AccessDeniedException("Students may only access their own records");
        }
    }

    public Long studentId() {
        requireRole("STUDENT");
        return studentRepository.findIdByUserSubject(subject())
                .orElseThrow(() -> new EntityNotFoundException("Student profile not found"));
    }

    public void requireTeacherAccess(Long teacherId) {
        if (hasRole("ADMIN")) {
            return;
        }
        Long ownId = teacherId();
        if (teacherId == null || !teacherId.equals(ownId)) {
            throw new AccessDeniedException("Teachers may only access their own records");
        }
    }

    public Long teacherId() {
        requireRole("TEACHER");
        return teacherRepository.findByUserKeycloakSubject(subject())
                .orElseThrow(() -> new EntityNotFoundException("Teacher profile not found"))
                .getId();
    }

    public boolean canReadSection(Long studyPlanId, String teacherSubject) {
        if (hasRole("ADMIN")) {
            return true;
        }
        if (hasRole("TEACHER") && subject().equals(teacherSubject)) {
            return true;
        }
        if (hasRole("STUDENT")) {
            Long ownPlanId = studentRepository.findByUserKeycloakSubject(subject())
                    .orElseThrow(() -> new EntityNotFoundException("Student profile not found"))
                    .getStudyPlan().getId();
            return ownPlanId.equals(studyPlanId);
        }
        return false;
    }

    public void requireSectionAccess(Long studyPlanId, String teacherSubject) {
        if (!canReadSection(studyPlanId, teacherSubject)) {
            throw new AccessDeniedException("This section is not accessible to the current user");
        }
    }

    private Jwt token() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Jwt jwt)
                || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("A validated JWT is required");
        }
        return jwt;
    }
}
