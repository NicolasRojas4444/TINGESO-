package siga.ipt.backend.services;

import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import siga.ipt.backend.dtos.CourseDto;
import siga.ipt.backend.mappers.AcademicMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.entitys.Course;
import siga.ipt.backend.entitys.CoursePrerequisite;
import siga.ipt.backend.entitys.Section;
import siga.ipt.backend.entitys.StudyPlan;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.repositories.AcademicRecordRepository;
import siga.ipt.backend.repositories.CoursePrerequisiteRepository;
import siga.ipt.backend.repositories.CourseRepository;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.SectionRepository;
import siga.ipt.backend.repositories.StudyPlanRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final AcademicMapper mapper;
    private final CurrentUserService currentUser;

    private final CourseRepository courseRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final DegreeProgramRepository degreeProgramRepository;
    private final CoursePrerequisiteRepository coursePrerequisiteRepository;
    private final SectionRepository sectionRepository;
    private final AcademicRecordRepository academicRecordRepository;

    private List<Course> getAllEntity() {
        return courseRepository.findAll();
    }

    private Course getByIdEntity(Long id) {
        validateId(id);

        return courseRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Course not found with ID: " + id
                ));
    }

    private List<Course> getByStudyPlanEntity(Long studyPlanId) {
        if (studyPlanId == null) {
            throw new IllegalArgumentException(
                    "The study plan ID is required"
            );
        }

        if (!studyPlanRepository.existsById(studyPlanId)) {
            throw new EntityNotFoundException(
                    "Study plan not found with ID: " + studyPlanId
            );
        }

        return courseRepository
                .findByStudyPlanIdOrderBySemesterAscCodeAsc(studyPlanId);
    }

    private List<Course> getPrerequisitesEntity(Long courseId) {
        getByIdEntity(courseId);

        return coursePrerequisiteRepository.findByCourseId(courseId)
                .stream()
                .map(CoursePrerequisite::getPrerequisite)
                .toList();
    }

    private Course createEntity(Long studyPlanId, Course data) {
        validateData(data);

        if (studyPlanId == null) {
            throw new IllegalArgumentException(
                    "The study plan ID is required"
            );
        }

        StudyPlan studyPlan = studyPlanRepository.findById(studyPlanId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Study plan not found with ID: " + studyPlanId
                ));

        lockDegreeProgram(studyPlan.getDegreeProgram().getId());

        validateUniqueCode(studyPlanId, data.getCode().trim(), null);

        Course course = new Course();
        course.setStudyPlan(studyPlan);
        applyData(course, data);

        return courseRepository.save(course);
    }

    private Course updateEntity(Long id, Course data) {
        validateData(data);

        Course course = getForUpdate(id);

        validateUniqueCode(
                course.getStudyPlan().getId(),
                data.getCode().trim(),
                id
        );

        validateSemesterChange(course, data.getSemester());
        validateScheduleCompatibility(course, data);

        applyData(course, data);

        return courseRepository.save(course);
    }

    private List<Course> setPrerequisitesEntity(
            Long courseId,
            List<Long> prerequisiteIds
    ) {
        Course course = getForUpdate(courseId);

        List<Course> prerequisites = validatePrerequisites(
                course,
                prerequisiteIds
        );

        List<CoursePrerequisite> previous =
                coursePrerequisiteRepository.findByCourseId(courseId);

        coursePrerequisiteRepository.deleteAll(previous);
        coursePrerequisiteRepository.flush();

        List<CoursePrerequisite> relationships = new ArrayList<>();

        for (Course prerequisite : prerequisites) {
            CoursePrerequisite relationship = new CoursePrerequisite();
            relationship.setCourse(course);
            relationship.setPrerequisite(prerequisite);
            relationships.add(relationship);
        }

        coursePrerequisiteRepository.saveAll(relationships);

        return prerequisites;
    }

    private void deleteEntity(Long id) {
        Course course = getForUpdate(id);

        if (academicRecordRepository
                .existsByRegistrationSectionCourseId(id)) {
            throw new IllegalStateException(
                    "A course included in academic history cannot be deleted"
            );
        }

        if (sectionRepository.existsByCourseId(id)) {
            throw new IllegalStateException(
                    "A course with associated sections cannot be deleted"
            );
        }

        if (!coursePrerequisiteRepository
                .findByPrerequisiteId(id).isEmpty()) {
            throw new IllegalStateException(
                    "A course required by other courses cannot be deleted"
            );
        }

        coursePrerequisiteRepository.deleteAll(
                coursePrerequisiteRepository.findByCourseId(id)
        );

        courseRepository.delete(course);
    }

    private Course getForUpdate(Long id) {
        validateId(id);

        Long degreeProgramId = courseRepository.findDegreeProgramIdById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Course not found with ID: " + id
                ));

        lockDegreeProgram(degreeProgramId);

        return getByIdEntity(id);
    }

    private void lockDegreeProgram(Long id) {
        degreeProgramRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Degree program not found with ID: " + id
                ));
    }

    private void validateUniqueCode(
            Long studyPlanId,
            String code,
            Long excludedCourseId
    ) {
        if (courseRepository.findByStudyPlanIdAndCode(studyPlanId, code)
                .filter(course -> !course.getId().equals(excludedCourseId))
                .isPresent()) {
            throw new IllegalArgumentException(
                    "Another course in this study plan already uses this code"
            );
        }
    }

    private List<Course> validatePrerequisites(
            Course course,
            List<Long> prerequisiteIds
    ) {
        if (prerequisiteIds == null) {
            throw new IllegalArgumentException(
                    "The prerequisite list is required; use an empty list "
                            + "for a course without prerequisites"
            );
        }

        if (prerequisiteIds.size() > 3) {
            throw new IllegalArgumentException(
                    "A course cannot have more than three prerequisites"
            );
        }

        if (new HashSet<>(prerequisiteIds).size() != prerequisiteIds.size()) {
            throw new IllegalArgumentException(
                    "Prerequisites cannot be duplicated"
            );
        }

        List<Course> prerequisites = new ArrayList<>();

        for (Long prerequisiteId : prerequisiteIds) {
            Course prerequisite = getByIdEntity(prerequisiteId);

            if (course.getId().equals(prerequisite.getId())) {
                throw new IllegalArgumentException(
                        "A course cannot require itself"
                );
            }

            if (!course.getStudyPlan().getId()
                    .equals(prerequisite.getStudyPlan().getId())) {
                throw new IllegalArgumentException(
                        "Prerequisites must belong to the same study plan"
                );
            }

            if (prerequisite.getSemester() >= course.getSemester()) {
                throw new IllegalArgumentException(
                        "Prerequisites must belong to earlier semesters"
                );
            }

            prerequisites.add(prerequisite);
        }

        return prerequisites;
    }

    private void validateSemesterChange(Course course, Integer semester) {
        List<CoursePrerequisite> prerequisites =
                coursePrerequisiteRepository.findByCourseId(course.getId());

        for (CoursePrerequisite relationship : prerequisites) {
            if (relationship.getPrerequisite().getSemester() >= semester) {
                throw new IllegalArgumentException(
                        "The new semester must be later than all prerequisites"
                );
            }
        }

        List<CoursePrerequisite> dependents =
                coursePrerequisiteRepository
                        .findByPrerequisiteId(course.getId());

        for (CoursePrerequisite relationship : dependents) {
            if (semester >= relationship.getCourse().getSemester()) {
                throw new IllegalArgumentException(
                        "The new semester must be earlier than the courses "
                                + "that require this course"
                );
            }
        }
    }

    private void validateScheduleCompatibility(Course course, Course data) {
        int currentHours = totalWeeklyHours(course);
        int requestedHours = totalWeeklyHours(data);

        if (currentHours == requestedHours) {
            return;
        }

        List<Section> sections =
                sectionRepository.findByCourseIdAndAcademicPeriodStatus(
                        course.getId(),
                        PeriodStatus.OPEN
                );

        for (Section section : sections) {
            if (section.getScheduleBlocks().size() * 2L != requestedHours) {
                throw new IllegalStateException(
                        "The requested TEL hours do not match the schedule "
                                + "of section: " + section.getId()
                );
            }
        }
    }

    private void applyData(Course course, Course data) {
        course.setCode(data.getCode().trim());
        course.setName(data.getName().trim());
        course.setSemester(data.getSemester());
        course.setTheoryHours(data.getTheoryHours());
        course.setExerciseHours(data.getExerciseHours());
        course.setLaboratoryHours(data.getLaboratoryHours());
        course.setSct(data.getSct());
    }

    private void validateId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "The course ID is required"
            );
        }
    }

    private void validateData(Course data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Course data is required"
            );
        }

        validateText(data.getCode(), "Code", 30);
        validateText(data.getName(), "Name", 200);

        if (data.getSemester() == null
                || data.getSemester() < 1
                || data.getSemester() > 4) {
            throw new IllegalArgumentException(
                    "The curriculum semester must be between one and four"
            );
        }

        if (data.getSct() == null || data.getSct() < 1 || data.getSct() > 7) {
            throw new IllegalArgumentException(
                    "SCT credits must be between one and seven"
            );
        }

        validateHours(data.getTheoryHours(), "Theory hours");
        validateHours(data.getExerciseHours(), "Exercise hours");
        validateHours(data.getLaboratoryHours(), "Laboratory hours");

        long total = (long) data.getTheoryHours()
                + data.getExerciseHours()
                + data.getLaboratoryHours();

        if (total >= 8) {
            throw new IllegalArgumentException(
                    "Total weekly TEL hours must be less than eight"
            );
        }
    }

    private void validateHours(Integer hours, String field) {
        if (hours == null || hours < 0) {
            throw new IllegalArgumentException(
                    field + " must be zero or greater"
            );
        }
    }

    private int totalWeeklyHours(Course course) {
        return course.getTheoryHours()
                + course.getExerciseHours()
                + course.getLaboratoryHours();
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

    public List<CourseDto> getAll() {
        currentUser.requireAcademicUser();
        return getAllEntity().stream().map(mapper::toDto).toList();
    }

    public CourseDto getById(Long id) {
        currentUser.requireAcademicUser();
        return mapper.toDto(getByIdEntity(id));
    }

    public List<CourseDto> getByStudyPlan(Long studyPlanId) {
        currentUser.requireAcademicUser();
        return getByStudyPlanEntity(studyPlanId).stream().map(mapper::toDto).toList();
    }

    public List<CourseDto> getPrerequisites(Long courseId) {
        currentUser.requireAcademicUser();
        return getPrerequisitesEntity(courseId).stream().map(mapper::toDto).toList();
    }

    @Transactional
    public CourseDto create(Long studyPlanId, CourseDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(createEntity(studyPlanId, mapper.toInput(data)));
    }

    @Transactional
    public CourseDto update(Long id, CourseDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(updateEntity(id, mapper.toInput(data)));
    }

    @Transactional
    public List<CourseDto> setPrerequisites(Long courseId, List<Long> prerequisiteIds) {
        currentUser.requireRole("ADMIN");
        return setPrerequisitesEntity(courseId, prerequisiteIds).stream().map(mapper::toDto).toList();
    }

    @Transactional
    public void delete(Long id) {
        currentUser.requireRole("ADMIN");
        deleteEntity(id);
    }
}
