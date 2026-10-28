package siga.ipt.backend.services;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import siga.ipt.backend.dtos.TeacherDto;
import siga.ipt.backend.mappers.AcademicMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.entitys.Teacher;
import siga.ipt.backend.entitys.User;
import siga.ipt.backend.entitys.enums.ActiveStatus;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.repositories.SectionRepository;
import siga.ipt.backend.repositories.TeacherRepository;
import siga.ipt.backend.repositories.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeacherService {

    private final AcademicMapper mapper;
    private final CurrentUserService currentUser;
    private final UserCleanupService userCleanup;

    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final SectionRepository sectionRepository;

    private List<Teacher> getAllEntity() {
        return teacherRepository.findAll();
    }

    private Teacher getByIdEntity(Long id) {
        validateId(id);

        return teacherRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Teacher not found with ID: " + id
                ));
    }



    private Teacher createEntity(Teacher data) {
        validateData(data);

        User userData = data.getUser();
        String run = normalizeRun(userData.getRun());
        String email = normalizeEmail(userData.getEmail());

        validateUniqueIdentity(run, email, null);
        validateText(
                userData.getKeycloakSubject(),
                "Keycloak subject",
                255
        );

        String subject = userData.getKeycloakSubject().trim();

        if (userRepository.existsByKeycloakSubject(subject)) {
            throw new IllegalArgumentException(
                    "A user with this Keycloak subject already exists"
            );
        }

        User user = new User();
        user.setKeycloakSubject(subject);
        user.setRun(run);
        user.setFullName(userData.getFullName().trim());
        user.setEmail(email);

        user = userRepository.save(user);

        Teacher teacher = new Teacher();
        teacher.setUser(user);
        teacher.setProfessionalTitle(data.getProfessionalTitle().trim());
        teacher.setAcademicDegree(data.getAcademicDegree().trim());
        teacher.setStatus(ActiveStatus.ACTIVE);

        return teacherRepository.save(teacher);
    }



    private Teacher updateEntity(Long id, Teacher data) {
        Teacher teacher = getForUpdate(id);
        validateData(data);

        User user = teacher.getUser();
        User userData = data.getUser();

        String run = normalizeRun(userData.getRun());
        String email = normalizeEmail(userData.getEmail());

        validateUniqueIdentity(run, email, user.getId());

        user.setRun(run);
        user.setFullName(userData.getFullName().trim());
        user.setEmail(email);

        teacher.setProfessionalTitle(data.getProfessionalTitle().trim());
        teacher.setAcademicDegree(data.getAcademicDegree().trim());

        userRepository.save(user);

        return teacherRepository.save(teacher);
    }



    private Teacher changeStatusEntity(Long id, ActiveStatus status) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "The teacher status is required"
            );
        }

        Teacher teacher = getForUpdate(id);

        if (status == ActiveStatus.INACTIVE
                && sectionRepository.existsByTeacherIdAndAcademicPeriodStatus(
                        id, PeriodStatus.OPEN)) {
            throw new IllegalStateException(
                    "A teacher with sections in an open academic period "
                            + "cannot become inactive"
            );
        }

        teacher.setStatus(status);

        return teacherRepository.save(teacher);
    }



    private void deleteEntity(Long id) {
        Teacher teacher = getForUpdate(id);

        if (sectionRepository.existsByTeacherId(id)) {
            throw new IllegalStateException(
                    "A teacher with associated sections cannot be deleted"
            );
        }

        teacherRepository.delete(teacher);
        teacherRepository.flush();
        userCleanup.deleteIfUnreferenced(teacher.getUser());
    }



    private Teacher getForUpdate(Long id) {
        validateId(id);

        return teacherRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Teacher not found with ID: " + id
                ));
    }



    private void validateId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "The teacher ID is required"
            );
        }
    }



    private void validateData(Teacher data) {
        if (data == null || data.getUser() == null) {
            throw new IllegalArgumentException(
                    "Teacher and user data are required"
            );
        }

        User user = data.getUser();

        String run = normalizeRun(user.getRun());
        String email = normalizeEmail(user.getEmail());

        validateText(run, "RUN", 10);
        validateText(user.getFullName(), "Full name", 200);
        validateText(email, "Email", 254);
        validateText(data.getProfessionalTitle(), "Professional title", 200);
        validateText(data.getAcademicDegree(), "Academic degree", 200);

        if (!run.matches("[0-9]{7,8}-[0-9K]")) {
            throw new IllegalArgumentException(
                    "The RUN must use the format 12345678-9"
            );
        }

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                    "The email format is invalid"
            );
        }
    }



    private void validateUniqueIdentity(
            String run,
            String email,
            Long excludedUserId
    ) {
        if (userRepository.findByRun(run)
                .filter(user -> !user.getId().equals(excludedUserId))
                .isPresent()) {
            throw new IllegalArgumentException(
                    "Another user already uses this RUN"
            );
        }

        if (userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !user.getId().equals(excludedUserId))
                .isPresent()) {
            throw new IllegalArgumentException(
                    "Another user already uses this email"
            );
        }
    }



    private void validateText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required"
            );
        }

        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must not exceed " + maxLength + " characters"
            );
        }
    }

    private String normalizeRun(String run) {
        return run == null
                ? null
                : run.replace(".", "").trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEmail(String email) {
        return email == null
                ? null
                : email.trim().toLowerCase(Locale.ROOT);
    }

    public List<TeacherDto> getAll() {
        currentUser.requireRole("ADMIN");
        return getAllEntity().stream().map(mapper::toDto).toList();
    }

    public TeacherDto getById(Long id) {
        currentUser.requireTeacherAccess(id);
        return mapper.toDto(getByIdEntity(id));
    }

    @Transactional
    public TeacherDto create(TeacherDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(createEntity(mapper.toInput(data)));
    }

    @Transactional
    public TeacherDto update(Long id, TeacherDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(updateEntity(id, mapper.toInput(data)));
    }

    @Transactional
    public TeacherDto changeStatus(Long id, TeacherDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(changeStatusEntity(id, data == null ? null : data.status()));
    }

    @Transactional
    public void delete(Long id) {
        currentUser.requireRole("ADMIN");
        deleteEntity(id);
    }
}
