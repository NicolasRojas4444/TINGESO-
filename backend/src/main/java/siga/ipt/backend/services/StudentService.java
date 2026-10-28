package siga.ipt.backend.services;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import siga.ipt.backend.dtos.StudentDto;
import siga.ipt.backend.mappers.AcademicMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.entitys.DegreeProgram;
import siga.ipt.backend.entitys.Student;
import siga.ipt.backend.entitys.StudyPlan;
import siga.ipt.backend.entitys.User;
import siga.ipt.backend.entitys.enums.ActiveStatus;
import siga.ipt.backend.entitys.enums.PlanStatus;
import siga.ipt.backend.entitys.enums.StudentStatus;
import siga.ipt.backend.repositories.AcademicRecordRepository;
import siga.ipt.backend.repositories.CourseRegistrationRepository;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.EnrollmentRepository;
import siga.ipt.backend.repositories.StudentRepository;
import siga.ipt.backend.repositories.StudyPlanRepository;
import siga.ipt.backend.repositories.UserRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentService {

    private final AcademicMapper mapper;
    private final CurrentUserService currentUser;
    private final UserCleanupService userCleanup;

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final DegreeProgramRepository degreeProgramRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final CourseRegistrationRepository courseRegistrationRepository;
    private final AcademicRecordRepository academicRecordRepository;
    private final EnrollmentRepository enrollmentRepository;


    private List<Student> getAllEntity() {
        return studentRepository.findAll();
    }


    private Student getByIdEntity(Long id) {
        validateId(id);
        return studentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Student not found with ID: " + id
                ));
    }


    private Student createEntity(Long degreeProgramId, Student data) {
        validateData(data);

        DegreeProgram degreeProgram = getActiveDegreeProgramForUpdate(
                degreeProgramId
        );

        StudyPlan studyPlan = getCurrentPlan(degreeProgram.getId());

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

        Student student = new Student();
        student.setUser(user);
        student.setStudyPlan(studyPlan);
        student.setAcademicStatus(StudentStatus.REGULAR);

        return studentRepository.save(student);
    }


    private Student updateEntity(Long id, Student data) {
        Student student = getForUpdate(id);
        validateData(data);

        User user = student.getUser();
        User userData = data.getUser();

        String run = normalizeRun(userData.getRun());
        String email = normalizeEmail(userData.getEmail());

        validateUniqueIdentity(run, email, user.getId());

        user.setRun(run);
        user.setFullName(userData.getFullName().trim());
        user.setEmail(email);

        userRepository.save(user);

        return studentRepository.save(student);
    }


    private Student changeStatusEntity(Long id, StudentStatus status) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "The student status is required"
            );
        }
        if (status != StudentStatus.REGULAR
                && status != StudentStatus.POSTPONED
                && status != StudentStatus.TEMPORARY_WITHDRAWAL) {
            throw new IllegalArgumentException(
                    "Only REGULAR, POSTPONED and TEMPORARY_WITHDRAWAL "
                            + "can be assigned manually"
            );
        }

        Student student = getForUpdate(id);
        if (student.getAcademicStatus() == StudentStatus.GRADUATED
                || student.getAcademicStatus() == StudentStatus.ELIMINATED) {
            throw new IllegalStateException("Statuses determined by academic closing cannot be changed manually");
        }
        student.setAcademicStatus(status);
        return studentRepository.save(student);
    }


    private Student changeStudyPlanEntity(Long id, Long studyPlanId) {
        validateId(id);
        if (studyPlanId == null) {
            throw new IllegalArgumentException("The study plan ID is required");
        }
        Long currentDegree = studentRepository.findDegreeProgramIdById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student not found with ID: " + id));
        Long targetDegree = studyPlanRepository.findDegreeProgramIdById(studyPlanId)
                .orElseThrow(() -> new EntityNotFoundException("Study plan not found"));
        java.util.stream.Stream.of(currentDegree, targetDegree).distinct().sorted()
                .forEach(degreeId -> degreeProgramRepository.findByIdForUpdate(degreeId)
                        .orElseThrow(() -> new EntityNotFoundException("Degree program not found")));
        Student student = studentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getStudyPlan().getDegreeProgram().getId().equals(currentDegree)) {
            throw new IllegalStateException("The student's career changed concurrently; retry the operation");
        }
        validateNoAcademicActivity(id);
        getActiveDegreeProgramForUpdate(targetDegree);
        StudyPlan plan = studyPlanRepository.findById(studyPlanId)
                .orElseThrow(() -> new EntityNotFoundException("Study plan not found"));
        student.setStudyPlan(plan);
        return studentRepository.save(student);
    }

    private void deleteEntity(Long id) {
        Student student = getForUpdate(id);
        validateNoAcademicActivity(id);

        if (enrollmentRepository.existsByStudentId(id)) {
            throw new IllegalStateException(
                    "A student with associated enrollment records "
                            + "cannot be deleted"
            );
        }
        studentRepository.delete(student);
        studentRepository.flush();
        userCleanup.deleteIfUnreferenced(student.getUser());
    }


    private Student getForUpdate(Long id) {
        validateId(id);

        Long degreeProgramId = studentRepository.findDegreeProgramIdById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student not found with ID: " + id));
        degreeProgramRepository.findByIdForUpdate(degreeProgramId)
                .orElseThrow(() -> new EntityNotFoundException("Degree program not found"));
        return studentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Student not found with ID: " + id
                ));
    }


    private DegreeProgram getActiveDegreeProgramForUpdate(Long id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "The degree program ID is required"
            );
        }
        DegreeProgram degreeProgram =
                degreeProgramRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Degree program not found with ID: " + id
                        ));
        if (degreeProgram.getStatus() != ActiveStatus.ACTIVE) {
            throw new IllegalStateException(
                    "An inactive degree program cannot receive students"
            );
        }
        return degreeProgram;
    }


    private StudyPlan getCurrentPlan(Long degreeProgramId) {
        List<StudyPlan> currentPlans =
                studyPlanRepository.findByDegreeProgramIdAndStatus(
                        degreeProgramId,
                        PlanStatus.CURRENT
                );
        if (currentPlans.size() != 1) {
            throw new IllegalStateException(
                    "The degree program must have exactly one current "
                            + "study plan to register a student"
            );
        }
        return currentPlans.get(0);
    }


    private void validateNoAcademicActivity(Long studentId) {
        if (courseRegistrationRepository.existsByStudentId(studentId)
                || academicRecordRepository
                        .existsByRegistrationStudentId(studentId)) {
            throw new IllegalStateException(
                    "A student with registrations or academic history "
                            + "cannot change study plans or be deleted"
            );
        }
    }


    private void validateId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "The student ID is required"
            );
        }
    }


    private void validateData(Student data) {
        if (data == null || data.getUser() == null) {
            throw new IllegalArgumentException(
                    "Student and user data are required"
            );
        }

        User user = data.getUser();

        String run = normalizeRun(user.getRun());
        String email = normalizeEmail(user.getEmail());

        validateText(run, "RUN", 10);
        validateText(user.getFullName(), "Full name", 200);
        validateText(email, "Email", 254);

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

    public List<StudentDto> getAll() {
        currentUser.requireRole("ADMIN");
        return getAllEntity().stream().map(mapper::toDto).toList();
    }

    public StudentDto getById(Long id) {
        currentUser.requireStudentAccess(id);
        return mapper.toDto(getByIdEntity(id));
    }

    @Transactional
    public StudentDto create(Long degreeProgramId, StudentDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(createEntity(degreeProgramId, mapper.toInput(data)));
    }

    @Transactional
    public StudentDto update(Long id, StudentDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(updateEntity(id, mapper.toInput(data)));
    }

    @Transactional
    public StudentDto changeStatus(Long id, StudentDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(changeStatusEntity(id, data == null ? null : data.academicStatus()));
    }

    @Transactional
    public StudentDto changeStudyPlan(Long id, Long studyPlanId) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(changeStudyPlanEntity(id, studyPlanId));
    }

    @Transactional
    public void delete(Long id) {
        currentUser.requireRole("ADMIN");
        deleteEntity(id);
    }
}
