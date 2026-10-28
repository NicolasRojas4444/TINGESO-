package siga.ipt.backend.services;

import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.dtos.CourseRegistrationDto;
import siga.ipt.backend.dtos.SectionDto;
import siga.ipt.backend.entitys.AcademicPeriod;
import siga.ipt.backend.entitys.AcademicRecord;
import siga.ipt.backend.entitys.Course;
import siga.ipt.backend.entitys.CourseRegistration;
import siga.ipt.backend.entitys.Section;
import siga.ipt.backend.entitys.Student;
import siga.ipt.backend.entitys.enums.GradeResult;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.entitys.enums.RegistrationStatus;
import siga.ipt.backend.entitys.enums.StudentStatus;
import siga.ipt.backend.mappers.AcademicMapper;
import siga.ipt.backend.repositories.AcademicPeriodRepository;
import siga.ipt.backend.repositories.AcademicRecordRepository;
import siga.ipt.backend.repositories.CoursePrerequisiteRepository;
import siga.ipt.backend.repositories.CourseRegistrationRepository;
import siga.ipt.backend.repositories.CourseRepository;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.EnrollmentRepository;
import siga.ipt.backend.repositories.FinalGradeRepository;
import siga.ipt.backend.repositories.SectionRepository;
import siga.ipt.backend.repositories.StudentRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseRegistrationService {

    private final StudentRepository studentRepository;
    private final DegreeProgramRepository degreeProgramRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final CoursePrerequisiteRepository coursePrerequisiteRepository;
    private final SectionRepository sectionRepository;
    private final CourseRegistrationRepository registrationRepository;
    private final AcademicRecordRepository academicRecordRepository;
    private final FinalGradeRepository finalGradeRepository;
    private final AcademicMapper mapper;
    private final CurrentUserService currentUser;

    public List<CourseRegistrationDto> getByStudent(Long studentId) {
        currentUser.requireStudentAccess(studentId);
        requireStudent(studentId);
        return registrationRepository.findByStudentIdOrderByRegisteredAtDesc(studentId)
                .stream().map(mapper::toDto).toList();
    }

    public List<CourseRegistrationDto> getBySection(Long sectionId) {
        Section section = requireSection(sectionId);
        currentUser.requireTeacherAccess(section.getTeacher().getId());
        return registrationRepository.findBySectionIdAndStatus(sectionId, RegistrationStatus.ACTIVE)
                .stream().map(mapper::toDto).toList();
    }

    public List<SectionDto> getAvailableSections(Long studentId, Long periodId) {
        currentUser.requireStudentAccess(studentId);
        Student student = requireStudent(studentId);
        requirePeriod(periodId);
        Progress progress = progress(student);
        Set<Long> current = new HashSet<>();
        List<Section> activeSections = new ArrayList<>();
        for (CourseRegistration registration : activeRegistrations(studentId, periodId)) {
            current.add(registration.getSection().getId());
            activeSections.add(registration.getSection());
        }
        return eligibleSections(student, periodId, progress, current).stream()
                .filter(section -> !current.contains(section.getId()))
                .filter(section -> activeSections.stream().noneMatch(other -> overlaps(section, other)))
                .map(mapper::toDto).toList();
    }

    @Transactional
    public List<CourseRegistrationDto> register(
            Long studentId, Long periodId, List<CourseRegistrationDto> selection) {
        currentUser.requireOwnStudent(studentId);
        Student student = lockContext(studentId, periodId);
        if (student.getAcademicStatus() != StudentStatus.REGULAR) {
            throw new IllegalStateException("Only REGULAR students can register courses");
        }
        if (!enrollmentRepository.existsByStudentIdAndAcademicPeriodId(studentId, periodId)) {
            throw new IllegalStateException("The student is not enrolled in this academic period");
        }
        if (selection == null || selection.isEmpty() || selection.size() > 6) {
            throw new IllegalArgumentException("Select between one and six sections in a single request");
        }

        List<CourseRegistration> existing = activeRegistrations(studentId, periodId);
        Set<Long> existingIds = new HashSet<>();
        List<Section> completeSelection = new ArrayList<>();
        for (CourseRegistration registration : existing) {
            existingIds.add(registration.getSection().getId());
            completeSelection.add(registration.getSection());
        }

        Progress progress = progress(student);
        List<Section> additions = new ArrayList<>();
        Set<Long> requestedIds = new HashSet<>();
        for (CourseRegistrationDto data : selection) {
            if (data == null || data.sectionId() == null || !requestedIds.add(data.sectionId())) {
                throw new IllegalArgumentException("Section IDs are required and cannot be duplicated");
            }
            Section section = requireSection(data.sectionId());
            if (existingIds.contains(section.getId())) {
                throw new IllegalArgumentException("The student is already registered in this section");
            }
            if (!section.getAcademicPeriod().getId().equals(periodId)
                    || !section.getCourse().getStudyPlan().getId().equals(student.getStudyPlan().getId())) {
                throw new IllegalArgumentException("Sections must belong to the student's plan and selected period");
            }
            validateCourseEligibility(section.getCourse(), progress);
            if (occupiedSeats(section) >= section.getCapacity()) {
                throw new IllegalStateException("Section has no available seats: " + section.getId());
            }
            additions.add(section);
            completeSelection.add(section);
        }

        List<Section> available = eligibleSections(student, periodId, progress, existingIds);
        validateSelection(completeSelection, available, progress);
        for (Section section : additions) {
            CourseRegistration registration = registrationRepository
                    .findByStudentIdAndSectionId(studentId, section.getId())
                    .orElseGet(CourseRegistration::new);
            registration.setStudent(student);
            registration.setSection(section);
            registration.setStatus(RegistrationStatus.ACTIVE);
            registration.setRegisteredAt(Instant.now());
            registration.setCancelledAt(null);
            registrationRepository.save(registration);
        }
        return activeRegistrations(studentId, periodId).stream().map(mapper::toDto).toList();
    }

    @Transactional
    public CourseRegistrationDto cancel(Long registrationId) {
        requireId(registrationId, "registration");
        Long studentId = registrationRepository.findStudentIdById(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Registration not found"));
        currentUser.requireOwnStudent(studentId);
        Long periodId = registrationRepository.findAcademicPeriodIdById(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Registration not found"));
        Student student = lockContext(studentId, periodId);
        CourseRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Registration not found"));
        if (registration.getStatus() != RegistrationStatus.ACTIVE) {
            throw new IllegalStateException("The registration is already cancelled");
        }
        List<Section> remainingSelection = activeRegistrations(studentId, periodId).stream()
                .filter(item -> !item.getId().equals(registrationId))
                .map(CourseRegistration::getSection).toList();
        Progress progress = progress(student);
        long pending = progress.curriculum().stream()
                .filter(course -> !progress.approved().contains(course.getId())).count();
        Set<Long> currentSectionIds = new HashSet<>();
        activeRegistrations(studentId, periodId).forEach(item -> currentSectionIds.add(item.getSection().getId()));
        int minimum = pending < 3
                ? eligibleSections(student, periodId, progress, currentSectionIds).size() : 3;
        if (remainingSelection.size() < minimum) {
            throw new IllegalStateException("Cancellation would leave fewer than " + minimum + " registered courses");
        }
        finalGradeRepository.findByRegistrationId(registrationId).ifPresent(finalGradeRepository::delete);
        registration.setStatus(RegistrationStatus.CANCELLED);
        registration.setCancelledAt(Instant.now());
        return mapper.toDto(registrationRepository.save(registration));
    }

    private Student lockContext(Long studentId, Long periodId) {
        requireId(studentId, "student");
        requireId(periodId, "academic period");
        Long degreeId = studentRepository.findDegreeProgramIdById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        degreeProgramRepository.findByIdForUpdate(degreeId)
                .orElseThrow(() -> new EntityNotFoundException("Degree program not found"));
        AcademicPeriod period = academicPeriodRepository.findByIdForUpdate(periodId)
                .orElseThrow(() -> new EntityNotFoundException("Academic period not found"));
        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new IllegalStateException("Registrations and cancellations require an OPEN period");
        }
        Student student = studentRepository.findByIdForUpdate(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
        if (!student.getStudyPlan().getDegreeProgram().getId().equals(degreeId)) {
            throw new IllegalStateException("The student's career changed concurrently; retry the operation");
        }
        return student;
    }

    private Progress progress(Student student) {
        Set<Long> approved = new HashSet<>();
        Map<Long, Long> failed = new HashMap<>();
        for (AcademicRecord record : academicRecordRepository
                .findHistoryByStudentId(student.getId(), PeriodStatus.CLOSED)) {
            Long courseId = record.getRegistration().getSection().getCourse().getId();
            if (record.getResult() == GradeResult.PASSED) {
                approved.add(courseId);
            } else {
                failed.merge(courseId, 1L, Long::sum);
            }
        }
        List<Course> curriculum = courseRepository
                .findByStudyPlanIdOrderBySemesterAscCodeAsc(student.getStudyPlan().getId());
        return new Progress(approved, failed, curriculum);
    }

    private List<Section> eligibleSections(
            Student student, Long periodId, Progress progress, Set<Long> currentSectionIds) {
        return sectionRepository.findByAcademicPeriodId(periodId).stream()
                .filter(section -> section.getCourse().getStudyPlan().getId()
                        .equals(student.getStudyPlan().getId()))
                .filter(section -> isCourseEligible(section.getCourse(), progress))
                .filter(section -> currentSectionIds.contains(section.getId())
                        || occupiedSeats(section) < section.getCapacity())
                .toList();
    }

    private boolean isCourseEligible(Course course, Progress progress) {
        return !progress.approved().contains(course.getId())
                && progress.failed().getOrDefault(course.getId(), 0L) < 2
                && coursePrerequisiteRepository.findByCourseId(course.getId()).stream()
                        .allMatch(link -> progress.approved().contains(link.getPrerequisite().getId()));
    }

    private void validateCourseEligibility(Course course, Progress progress) {
        if (!isCourseEligible(course, progress)) {
            throw new IllegalStateException("Course is already passed, has two failures, or has unpassed prerequisites: "
                    + course.getCode());
        }
    }

    private void validateSelection(List<Section> selection, List<Section> available, Progress progress) {
        long remaining = progress.curriculum().stream()
                .filter(course -> !progress.approved().contains(course.getId())).count();
        int minimum = remaining < 3 ? available.size() : 3;
        if (selection.size() < minimum || selection.size() > 6) {
            throw new IllegalArgumentException("The resulting course load must contain "
                    + minimum + " to six available courses");
        }
        Set<Long> selectedCourses = new HashSet<>();
        for (Section section : selection) {
            if (!selectedCourses.add(section.getCourse().getId())) {
                throw new IllegalArgumentException("A course cannot be registered twice in the same period");
            }
            validateCourseEligibility(section.getCourse(), progress);
        }
        for (int left = 0; left < selection.size(); left++) {
            for (int right = left + 1; right < selection.size(); right++) {
                if (overlaps(selection.get(left), selection.get(right))) {
                    throw new IllegalArgumentException("Selected sections have conflicting schedules");
                }
            }
        }
        for (Section section : selection) {
            for (Section pending : available) {
                if (pending.getCourse().getSemester() < section.getCourse().getSemester()
                        && !selectedCourses.contains(pending.getCourse().getId())) {
                    throw new IllegalStateException("Available pending courses from earlier semesters must be selected first");
                }
            }
        }
    }

    private boolean overlaps(Section left, Section right) {
        return left.getScheduleBlocks().stream().anyMatch(a -> right.getScheduleBlocks().stream()
                .anyMatch(b -> a.getDayOfWeek() == b.getDayOfWeek() && a.getModule().equals(b.getModule())));
    }

    private long occupiedSeats(Section section) {
        return registrationRepository.countBySectionIdAndStatus(section.getId(), RegistrationStatus.ACTIVE);
    }

    private List<CourseRegistration> activeRegistrations(Long studentId, Long periodId) {
        return registrationRepository.findByStudentIdAndSectionAcademicPeriodIdAndStatus(
                studentId, periodId, RegistrationStatus.ACTIVE);
    }

    private Student requireStudent(Long id) {
        requireId(id, "student");
        return studentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Student not found"));
    }

    private Section requireSection(Long id) {
        requireId(id, "section");
        return sectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Section not found"));
    }

    private void requirePeriod(Long id) {
        requireId(id, "academic period");
        AcademicPeriod period = academicPeriodRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Academic period not found"));
        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new IllegalStateException("Available registration choices require an OPEN period");
        }
    }

    private void requireId(Long id, String field) {
        if (id == null) {
            throw new IllegalArgumentException("The " + field + " ID is required");
        }
    }

    private record Progress(Set<Long> approved, Map<Long, Long> failed, List<Course> curriculum) {
    }
}
