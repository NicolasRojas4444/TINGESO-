package siga.ipt.backend.services;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.dtos.SelfRegistrationRole;
import siga.ipt.backend.dtos.UserDto;
import siga.ipt.backend.dtos.UserProfileRecoveryRequest;
import siga.ipt.backend.dtos.UserProfileRecoveryResponse;
import siga.ipt.backend.entitys.User;
import siga.ipt.backend.mappers.AcademicMapper;
import siga.ipt.backend.repositories.AcademicPeriodRepository;
import siga.ipt.backend.repositories.StudentRepository;
import siga.ipt.backend.repositories.TeacherRepository;
import siga.ipt.backend.repositories.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserRecoveryService {

    private final CurrentUserService currentUser;
    private final UserRepository users;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final AcademicPeriodRepository periods;
    private final StudentService studentService;
    private final TeacherService teacherService;
    private final UserCleanupService accounts;
    private final EntityManager entityManager;
    private final AcademicMapper mapper;
    private final KeycloakRegistrationService keycloak;

    public List<UserDto> getSuspendedUsers() {
        currentUser.requireRole("ADMIN");
        return users.findByAccessSuspendedTrue().stream().map(mapper::toDto).toList();
    }

    public List<UserDto> getLinkCandidates(SelfRegistrationRole role) {
        currentUser.requireRole("ADMIN");
        if (role == null) {
            throw new IllegalArgumentException("Select STUDENT or TEACHER");
        }
        return users.findByAccessSuspendedFalse().stream()
                .filter(user -> role == SelfRegistrationRole.STUDENT
                        ? !students.existsByUserId(user.getId()) : !teachers.existsByUserId(user.getId()))
                .map(mapper::toDto).toList();
    }

    @Transactional
    public UserProfileRecoveryResponse linkProfile(Long userId, UserProfileRecoveryRequest request) {
        currentUser.requireRole("ADMIN");
        if (userId == null || request == null || request.role() == null) {
            throw new IllegalArgumentException("A user ID and STUDENT or TEACHER role are required");
        }
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (user.isAccessSuspended()) {
            throw new IllegalStateException("Use account recovery for suspended identities");
        }
        boolean profileExists = request.role() == SelfRegistrationRole.STUDENT
                ? students.existsByUserId(userId) : teachers.existsByUserId(userId);
        if (profileExists) {
            throw new IllegalStateException("This identity already has the requested academic profile");
        }
        keycloak.requireEnabledAccountRole(user.getKeycloakSubject(), request.role());
        Long studentId = null;
        Long teacherId = null;
        if (request.role() == SelfRegistrationRole.STUDENT) {
            studentId = studentService.restoreProfile(request.degreeProgramId(), user).id();
        } else {
            teacherId = teacherService.restoreProfile(user, request.professionalTitle(), request.academicDegree()).id();
        }
        entityManager.flush();
        // Linking an active identity does not toggle Keycloak access, modify roles,
        // reset the token cutoff or discard historical/other-profile references.
        return new UserProfileRecoveryResponse(userId, user.getKeycloakSubject(), request.role(), studentId, teacherId);
    }

    @Transactional
    public UserProfileRecoveryResponse restoreProfile(Long userId, UserProfileRecoveryRequest request) {
        currentUser.requireRole("ADMIN");
        if (userId == null || request == null || request.role() == null) {
            throw new IllegalArgumentException("A user ID and STUDENT or TEACHER role are required");
        }
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        if (!user.isAccessSuspended() || students.existsByUserId(userId) || teachers.existsByUserId(userId)
                || periods.existsByClosedById(userId)) {
            throw new IllegalStateException("Only suspended identities without profiles or historical references can be recovered");
        }
        Long studentId = null;
        Long teacherId = null;
        if (request.role() == SelfRegistrationRole.STUDENT) {
            studentId = studentService.restoreProfile(request.degreeProgramId(), user).id();
        } else {
            teacherId = teacherService.restoreProfile(user, request.professionalTitle(), request.academicDegree()).id();
        }
        entityManager.flush();
        accounts.restoreAccess(user, request.role());
        return new UserProfileRecoveryResponse(userId, user.getKeycloakSubject(), request.role(), studentId, teacherId);
    }
}
