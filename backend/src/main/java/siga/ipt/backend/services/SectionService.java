package siga.ipt.backend.services;

import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import siga.ipt.backend.dtos.SectionDto;
import siga.ipt.backend.mappers.AcademicMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.entitys.AcademicPeriod;
import siga.ipt.backend.entitys.Course;
import siga.ipt.backend.entitys.Section;
import siga.ipt.backend.entitys.SectionScheduleBlock;
import siga.ipt.backend.entitys.Teacher;
import siga.ipt.backend.entitys.enums.ActiveStatus;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.entitys.enums.RegistrationStatus;
import siga.ipt.backend.repositories.AcademicPeriodRepository;
import siga.ipt.backend.repositories.CourseRegistrationRepository;
import siga.ipt.backend.repositories.CourseRepository;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.SectionRepository;
import siga.ipt.backend.repositories.TeacherRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SectionService {

    private final AcademicMapper mapper;
    private final CurrentUserService currentUser;

    private final SectionRepository sectionRepository;
    private final CourseRepository courseRepository;
    private final DegreeProgramRepository degreeProgramRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final TeacherRepository teacherRepository;
    private final CourseRegistrationRepository courseRegistrationRepository;

    private List<Section> getAllEntity() {
        return sectionRepository.findAll();
    }

    private Section getByIdEntity(Long id) {
        validateId(id, "section");

        return sectionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Section not found with ID: " + id
                ));
    }

    private List<Section> getByPeriodEntity(Long periodId) {
        validateId(periodId, "academic period");

        if (!academicPeriodRepository.existsById(periodId)) {
            throw new EntityNotFoundException(
                    "Academic period not found with ID: " + periodId
            );
        }

        return sectionRepository.findByAcademicPeriodId(periodId);
    }

    private List<Section> getByTeacherEntity(Long teacherId) {
        validateId(teacherId, "teacher");

        if (!teacherRepository.existsById(teacherId)) {
            throw new EntityNotFoundException(
                    "Teacher not found with ID: " + teacherId
            );
        }

        return sectionRepository.findByTeacherId(teacherId);
    }

    private Section createEntity(
            Long courseId,
            Long periodId,
            Long teacherId,
            Section data
    ) {
        Course course = getCourseForOffering(courseId);
        AcademicPeriod period = getOpenPeriodForUpdate(periodId);
        Teacher teacher = getActiveTeacherForUpdate(teacherId);

        validateData(course, data);

        if (sectionRepository.existsByCourseIdAndAcademicPeriodId(
                courseId, periodId)) {
            throw new IllegalArgumentException(
                    "This course already has a section in this academic period"
            );
        }

        validateTeacherSchedule(
                teacherId,
                periodId,
                data.getScheduleBlocks(),
                null
        );

        Section section = new Section();
        section.setCourse(course);
        section.setAcademicPeriod(period);
        section.setTeacher(teacher);
        section.setCapacity(data.getCapacity());

        List<SectionScheduleBlock> blocks = copyScheduleBlocks(
                data.getScheduleBlocks()
        );

        for (SectionScheduleBlock block : blocks) {
            block.setSection(section);
            section.getScheduleBlocks().add(block);
        }

        return sectionRepository.save(section);
    }

    private Section updateEntity(Long id, Long teacherId, Section data) {
        Section section = getForUpdate(id);
        Teacher teacher = getActiveTeacherForUpdate(teacherId);

        validateData(section.getCourse(), data);

        long registeredStudents =
                courseRegistrationRepository.countBySectionIdAndStatus(
                        id,
                        RegistrationStatus.ACTIVE
                );

        if (data.getCapacity() < registeredStudents) {
            throw new IllegalStateException(
                    "Capacity cannot be lower than the number "
                            + "of active registrations"
            );
        }

        Long periodId = section.getAcademicPeriod().getId();

        validateTeacherSchedule(
                teacherId,
                periodId,
                data.getScheduleBlocks(),
                id
        );

        List<Section> otherStudentSections =
                courseRegistrationRepository
                        .findOtherSectionsForEnrolledStudents(
                                id,
                                periodId,
                                RegistrationStatus.ACTIVE
                        );

        validateNoScheduleConflicts(
                data.getScheduleBlocks(),
                otherStudentSections,
                id,
                "The proposed schedule conflicts with an enrolled "
                        + "student's other section"
        );

        List<SectionScheduleBlock> blocks = copyScheduleBlocks(
                data.getScheduleBlocks()
        );

        section.getScheduleBlocks().clear();
        sectionRepository.flush();

        section.setTeacher(teacher);
        section.setCapacity(data.getCapacity());

        for (SectionScheduleBlock block : blocks) {
            block.setSection(section);
            section.getScheduleBlocks().add(block);
        }

        return sectionRepository.save(section);
    }

    private void deleteEntity(Long id) {
        Section section = getForUpdate(id);

        if (courseRegistrationRepository.existsBySectionId(id)) {
            throw new IllegalStateException(
                    "A section with associated registrations cannot be deleted"
            );
        }

        sectionRepository.delete(section);
    }

    private Section getForUpdate(Long id) {
        validateId(id, "section");

        Long courseId = sectionRepository.findCourseIdById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Section not found with ID: " + id
                ));

        getCourseForOffering(courseId);

        Long periodId = sectionRepository.findAcademicPeriodIdById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Section not found with ID: " + id
                ));

        getOpenPeriodForUpdate(periodId);

        return getByIdEntity(id);
    }

    private Course getCourseForOffering(Long courseId) {
        validateId(courseId, "course");

        Long degreeProgramId =
                courseRepository.findDegreeProgramIdById(courseId)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Course not found with ID: " + courseId
                        ));

        degreeProgramRepository.findByIdForUpdate(degreeProgramId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Degree program not found with ID: " + degreeProgramId
                ));

        return courseRepository.findById(courseId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Course not found with ID: " + courseId
                ));
    }

    private AcademicPeriod getOpenPeriodForUpdate(Long periodId) {
        validateId(periodId, "academic period");

        AcademicPeriod period =
                academicPeriodRepository.findByIdForUpdate(periodId)
                        .orElseThrow(() -> new EntityNotFoundException(
                                "Academic period not found with ID: " + periodId
                        ));

        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new IllegalStateException(
                    "Sections can only be managed in open academic periods"
            );
        }

        return period;
    }

    private Teacher getActiveTeacherForUpdate(Long teacherId) {
        validateId(teacherId, "teacher");

        Teacher teacher = teacherRepository.findByIdForUpdate(teacherId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Teacher not found with ID: " + teacherId
                ));

        if (teacher.getStatus() != ActiveStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only active teachers can be assigned to sections"
            );
        }

        return teacher;
    }

    private void validateTeacherSchedule(
            Long teacherId,
            Long periodId,
            List<SectionScheduleBlock> blocks,
            Long excludedSectionId
    ) {
        List<Section> otherSections =
                sectionRepository.findByTeacherIdAndAcademicPeriodId(
                        teacherId,
                        periodId
                );

        validateNoScheduleConflicts(
                blocks,
                otherSections,
                excludedSectionId,
                "The teacher already has another section at this time"
        );
    }

    private void validateNoScheduleConflicts(
            List<SectionScheduleBlock> proposedBlocks,
            List<Section> otherSections,
            Long excludedSectionId,
            String message
    ) {
        Set<String> proposedKeys = new HashSet<>();

        for (SectionScheduleBlock block : proposedBlocks) {
            proposedKeys.add(scheduleKey(block));
        }

        for (Section otherSection : otherSections) {
            if (otherSection.getId().equals(excludedSectionId)) {
                continue;
            }

            for (SectionScheduleBlock block : otherSection.getScheduleBlocks()) {
                if (proposedKeys.contains(scheduleKey(block))) {
                    throw new IllegalStateException(
                            message + ". Section ID: " + otherSection.getId()
                    );
                }
            }
        }
    }

    private void validateData(Course course, Section data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Section data is required"
            );
        }

        if (data.getCapacity() == null
                || data.getCapacity() < 1
                || data.getCapacity() > 60) {
            throw new IllegalArgumentException(
                    "Capacity must be between one and sixty"
            );
        }

        List<SectionScheduleBlock> blocks = data.getScheduleBlocks();

        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one schedule block is required"
            );
        }

        Set<String> keys = new HashSet<>();

        for (SectionScheduleBlock block : blocks) {
            if (block == null
                    || block.getDayOfWeek() == null
                    || block.getModule() == null) {
                throw new IllegalArgumentException(
                        "Each schedule block requires a day and module"
                );
            }

            if (block.getDayOfWeek().getValue() > 5) {
                throw new IllegalArgumentException(
                        "Schedule days must be Monday through Friday"
                );
            }

            if (block.getModule() < 1 || block.getModule() > 6) {
                throw new IllegalArgumentException(
                        "Schedule modules must be between one and six"
                );
            }

            if (!keys.add(scheduleKey(block))) {
                throw new IllegalArgumentException(
                        "Schedule blocks cannot be duplicated"
                );
            }
        }

        long weeklyHours = (long) course.getTheoryHours()
                + course.getExerciseHours()
                + course.getLaboratoryHours();

        if (blocks.size() * 2L != weeklyHours) {
            throw new IllegalArgumentException(
                    "Each schedule block represents two pedagogical hours; "
                            + "the schedule must match the course's weekly TEL"
            );
        }
    }

    private List<SectionScheduleBlock> copyScheduleBlocks(
            List<SectionScheduleBlock> source
    ) {
        List<SectionScheduleBlock> copies = new ArrayList<>();

        for (SectionScheduleBlock original : source) {
            SectionScheduleBlock copy = new SectionScheduleBlock();
            copy.setDayOfWeek(original.getDayOfWeek());
            copy.setModule(original.getModule());
            copies.add(copy);
        }

        return copies;
    }

    private String scheduleKey(SectionScheduleBlock block) {
        return block.getDayOfWeek().name() + ":" + block.getModule();
    }

    private void validateId(Long id, String field) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "The " + field + " ID is required"
            );
        }
    }

    public List<SectionDto> getAll() {
        currentUser.requireRole("ADMIN");
        return getAllEntity().stream().map(mapper::toDto).toList();
    }

    public SectionDto getById(Long id) {
        currentUser.requireAcademicUser();
        Section section = getByIdEntity(id);
        currentUser.requireSectionAccess(section.getCourse().getStudyPlan().getId(),
                section.getTeacher().getUser().getKeycloakSubject());
        return mapper.toDto(section);
    }

    public List<SectionDto> getByPeriod(Long periodId) {
        currentUser.requireAcademicUser();
        return getByPeriodEntity(periodId).stream()
                .filter(section -> currentUser.canReadSection(
                        section.getCourse().getStudyPlan().getId(),
                        section.getTeacher().getUser().getKeycloakSubject()))
                .map(mapper::toDto).toList();
    }

    public List<SectionDto> getByTeacher(Long teacherId) {
        currentUser.requireTeacherAccess(teacherId);
        return getByTeacherEntity(teacherId).stream().map(mapper::toDto).toList();
    }

    @Transactional
    public SectionDto create(Long courseId, Long periodId, Long teacherId, SectionDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(createEntity(courseId, periodId, teacherId, mapper.toInput(data)));
    }

    @Transactional
    public SectionDto update(Long id, Long teacherId, SectionDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(updateEntity(id, teacherId, mapper.toInput(data)));
    }

    @Transactional
    public void delete(Long id) {
        currentUser.requireRole("ADMIN");
        deleteEntity(id);
    }
}
