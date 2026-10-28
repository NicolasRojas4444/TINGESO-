package siga.ipt.backend.services;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.dtos.AcademicPeriodDto;
import siga.ipt.backend.entitys.AcademicPeriod;
import siga.ipt.backend.entitys.AcademicRecord;
import siga.ipt.backend.entitys.Course;
import siga.ipt.backend.entitys.CourseRegistration;
import siga.ipt.backend.entitys.FinalGrade;
import siga.ipt.backend.entitys.Student;
import siga.ipt.backend.entitys.enums.GradeResult;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.entitys.enums.RegistrationStatus;
import siga.ipt.backend.entitys.enums.StudentStatus;
import siga.ipt.backend.mappers.AcademicMapper;
import siga.ipt.backend.repositories.AcademicPeriodRepository;
import siga.ipt.backend.repositories.AcademicRecordRepository;
import siga.ipt.backend.repositories.CourseRegistrationRepository;
import siga.ipt.backend.repositories.CourseRepository;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.FinalGradeRepository;
import siga.ipt.backend.repositories.SectionRepository;
import siga.ipt.backend.repositories.StudentRepository;
import siga.ipt.backend.repositories.UserRepository;

@Service
@RequiredArgsConstructor
public class AcademicClosingService {

    private static final BigDecimal PASSING_GRADE = new BigDecimal("4.0");

    private final DegreeProgramRepository degreeProgramRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final SectionRepository sectionRepository;
    private final CourseRegistrationRepository registrationRepository;
    private final FinalGradeRepository finalGradeRepository;
    private final AcademicRecordRepository academicRecordRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final AcademicMapper mapper;
    private final CurrentUserService currentUser;

    @Transactional
    public AcademicPeriodDto close(Long periodId) {
        currentUser.requireRole("ADMIN");
        if (periodId == null) {
            throw new IllegalArgumentException("The academic period ID is required");
        }
        List<Long> degreeIds = sectionRepository.findDegreeProgramIdsByAcademicPeriodId(periodId)
                .stream().distinct().sorted().toList();
        for (Long degreeId : degreeIds) {
            degreeProgramRepository.findByIdForUpdate(degreeId)
                    .orElseThrow(() -> new EntityNotFoundException("Degree program not found"));
        }
        AcademicPeriod period = academicPeriodRepository.findByIdForUpdate(periodId)
                .orElseThrow(() -> new EntityNotFoundException("Academic period not found"));
        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new IllegalStateException("Only OPEN academic periods can be closed");
        }
        if (!new HashSet<>(degreeIds).containsAll(
                sectionRepository.findDegreeProgramIdsByAcademicPeriodId(periodId))) {
            throw new IllegalStateException("The academic offering changed concurrently; retry closing");
        }

        List<CourseRegistration> registrations = registrationRepository
                .findBySectionAcademicPeriodIdAndStatus(periodId, RegistrationStatus.ACTIVE);
        Map<Long, FinalGrade> grades = finalGradeRepository
                .findByRegistrationSectionAcademicPeriodIdAndRegistrationStatus(periodId, RegistrationStatus.ACTIVE)
                .stream().collect(Collectors.toMap(grade -> grade.getRegistration().getId(), Function.identity()));
        List<Long> incompleteSections = registrations.stream()
                .filter(registration -> !grades.containsKey(registration.getId()))
                .map(registration -> registration.getSection().getId()).distinct().sorted().toList();
        if (!incompleteSections.isEmpty()) {
            throw new IllegalStateException("Final grades are missing in sections: " + incompleteSections);
        }

        List<Long> studentIds = registrations.stream().map(registration -> registration.getStudent().getId())
                .distinct().sorted().toList();
        Map<Long, Student> students = new HashMap<>();
        for (Long studentId : studentIds) {
            Student student = studentRepository.findByIdForUpdate(studentId)
                    .orElseThrow(() -> new EntityNotFoundException("Student not found"));
            students.put(studentId, student);
        }

        Instant closedAt = Instant.now();
        List<AcademicRecord> records = new ArrayList<>();
        for (CourseRegistration registration : registrations) {
            if (academicRecordRepository.existsByRegistrationId(registration.getId())) {
                throw new IllegalStateException("This registration has already been consolidated: "
                        + registration.getId());
            }
            records.add(snapshot(registration, grades.get(registration.getId()), closedAt));
        }
        academicRecordRepository.saveAll(records);

        for (Student student : students.values()) {
            List<AcademicRecord> history = new ArrayList<>(academicRecordRepository
                    .findHistoryByStudentId(student.getId(), PeriodStatus.CLOSED));
            records.stream().filter(record -> record.getRegistration().getStudent().getId().equals(student.getId()))
                    .forEach(history::add);
            updateAcademicStatus(student, history);
        }
        studentRepository.saveAll(students.values());
        period.setClosedAt(closedAt);
        period.setClosedBy(userRepository.findByKeycloakSubject(currentUser.subject()).orElse(null));
        period.setStatus(PeriodStatus.CLOSED);
        return mapper.toDto(academicPeriodRepository.save(period));
    }

    private AcademicRecord snapshot(CourseRegistration registration, FinalGrade grade, Instant closedAt) {
        Course course = registration.getSection().getCourse();
        AcademicRecord record = new AcademicRecord();
        record.setRegistration(registration);
        record.setCourseCode(course.getCode());
        record.setCourseName(course.getName());
        record.setCurriculumSemester(course.getSemester());
        record.setTheoryHours(course.getTheoryHours());
        record.setExerciseHours(course.getExerciseHours());
        record.setLaboratoryHours(course.getLaboratoryHours());
        record.setSct(course.getSct());
        record.setFinalGrade(grade.getValue());
        record.setResult(grade.getValue().compareTo(PASSING_GRADE) >= 0 ? GradeResult.PASSED : GradeResult.FAILED);
        record.setConsolidatedAt(closedAt);
        return record;
    }

    private void updateAcademicStatus(Student student, List<AcademicRecord> history) {
        Set<Long> approved = new HashSet<>();
        Map<Long, Long> failures = new HashMap<>();
        for (AcademicRecord record : history) {
            Long courseId = record.getRegistration().getSection().getCourse().getId();
            if (record.getResult() == GradeResult.PASSED) {
                approved.add(courseId);
            } else {
                failures.merge(courseId, 1L, Long::sum);
            }
        }
        if (failures.values().stream().anyMatch(count -> count >= 2)) {
            student.setAcademicStatus(StudentStatus.ELIMINATED);
            return;
        }
        List<Course> curriculum = courseRepository
                .findByStudyPlanIdOrderBySemesterAscCodeAsc(student.getStudyPlan().getId());
        if (!curriculum.isEmpty() && curriculum.stream().allMatch(course -> approved.contains(course.getId()))) {
            student.setAcademicStatus(StudentStatus.GRADUATED);
        }
    }
}
