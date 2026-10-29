package siga.ipt.backend.mappers;

import java.util.ArrayList;
import org.springframework.stereotype.Component;
import siga.ipt.backend.dtos.AcademicPeriodDto;
import siga.ipt.backend.dtos.AcademicRecordDto;
import siga.ipt.backend.dtos.CourseDto;
import siga.ipt.backend.dtos.CourseRegistrationDto;
import siga.ipt.backend.dtos.DegreeProgramDto;
import siga.ipt.backend.dtos.FinalGradeDto;
import siga.ipt.backend.dtos.SectionDto;
import siga.ipt.backend.dtos.SectionScheduleBlockDto;
import siga.ipt.backend.dtos.StudentDto;
import siga.ipt.backend.dtos.StudyPlanDto;
import siga.ipt.backend.dtos.TeacherDto;
import siga.ipt.backend.dtos.UserDto;
import siga.ipt.backend.entitys.AcademicPeriod;
import siga.ipt.backend.entitys.AcademicRecord;
import siga.ipt.backend.entitys.Course;
import siga.ipt.backend.entitys.CourseRegistration;
import siga.ipt.backend.entitys.DegreeProgram;
import siga.ipt.backend.entitys.FinalGrade;
import siga.ipt.backend.entitys.Section;
import siga.ipt.backend.entitys.SectionScheduleBlock;
import siga.ipt.backend.entitys.Student;
import siga.ipt.backend.entitys.StudyPlan;
import siga.ipt.backend.entitys.Teacher;
import siga.ipt.backend.entitys.User;
import siga.ipt.backend.entitys.enums.GradeResult;

@Component
public class AcademicMapper {

    public UserDto toDto(User user) {
        return new UserDto(user.getId(), user.getKeycloakSubject(), user.getRun(),
                user.getFullName(), user.getEmail());
    }

    public DegreeProgramDto toDto(DegreeProgram entity) {
        return new DegreeProgramDto(entity.getId(), entity.getCode(), entity.getName(),
                entity.getDescription(), entity.getDurationSemesters(), entity.getStatus());
    }

    public AcademicPeriodDto toDto(AcademicPeriod entity) {
        return new AcademicPeriodDto(entity.getId(), entity.getYear(), entity.getSemester(),
                entity.getStatus(), entity.getClosedAt(),
                entity.getClosedBy() == null ? null : entity.getClosedBy().getId());
    }

    public StudyPlanDto toDto(StudyPlan entity) {
        return new StudyPlanDto(entity.getId(), entity.getDegreeProgram().getId(),
                entity.getVersion(), entity.getStatus());
    }

    public TeacherDto toDto(Teacher entity) {
        return new TeacherDto(entity.getId(), toDto(entity.getUser()),
                entity.getProfessionalTitle(), entity.getAcademicDegree(), entity.getStatus());
    }

    public StudentDto toDto(Student entity) {
        return new StudentDto(entity.getId(), toDto(entity.getUser()),
                entity.getStudyPlan().getId(), entity.getStudyPlan().getDegreeProgram().getId(),
                entity.getAcademicStatus());
    }

    public CourseDto toDto(Course entity) {
        return new CourseDto(entity.getId(), entity.getStudyPlan().getId(), entity.getCode(),
                entity.getName(), entity.getSemester(), entity.getTheoryHours(),
                entity.getExerciseHours(), entity.getLaboratoryHours(), entity.getSct());
    }

    public SectionScheduleBlockDto toDto(SectionScheduleBlock entity) {
        return new SectionScheduleBlockDto(entity.getId(), entity.getDayOfWeek(), entity.getModule());
    }

    public SectionDto toDto(Section entity, long registeredCount) {
        return new SectionDto(entity.getId(), entity.getCourse().getId(),
                entity.getAcademicPeriod().getId(), entity.getTeacher().getId(),
                entity.getCourse().getName(), entity.getTeacher().getUser().getFullName(),
                entity.getCapacity(), registeredCount, Math.max(0L, entity.getCapacity() - registeredCount),
                entity.getScheduleBlocks().stream().map(this::toDto).toList());
    }

    public AcademicRecordDto toDto(AcademicRecord entity) {
        CourseRegistration registration = entity.getRegistration();
        return new AcademicRecordDto(entity.getId(), registration.getId(),
                registration.getStudent().getId(), registration.getSection().getId(),
                registration.getSection().getCourse().getId(),
                registration.getSection().getAcademicPeriod().getId(),
                entity.getCourseCode(), entity.getCourseName(), entity.getCurriculumSemester(),
                entity.getTheoryHours(), entity.getExerciseHours(), entity.getLaboratoryHours(),
                entity.getSct(), entity.getFinalGrade(), entity.getResult(), entity.getConsolidatedAt());
    }

    public CourseRegistrationDto toDto(CourseRegistration entity) {
        return new CourseRegistrationDto(entity.getId(), entity.getStudent().getId(),
                entity.getSection().getId(), entity.getSection().getCourse().getId(),
                entity.getSection().getAcademicPeriod().getId(),
                entity.getStudent().getUser().getFullName(), entity.getSection().getCourse().getName(),
                entity.getStatus(), entity.getRegisteredAt(), entity.getCancelledAt());
    }

    public FinalGradeDto toDto(FinalGrade entity, GradeResult result) {
        CourseRegistration registration = entity.getRegistration();
        return new FinalGradeDto(entity.getId(), registration.getId(),
                registration.getStudent().getId(), registration.getSection().getId(),
                entity.getValue(), result);
    }

    private User inputUser(UserDto data) {
        if (data == null) {
            return null;
        }
        User entity = new User();
        entity.setKeycloakSubject(data.keycloakSubject());
        entity.setRun(data.run());
        entity.setFullName(data.fullName());
        entity.setEmail(data.email());
        return entity;
    }

    public DegreeProgram toInput(DegreeProgramDto data) {
        if (data == null) {
            return null;
        }
        DegreeProgram entity = new DegreeProgram();
        entity.setCode(data.code());
        entity.setName(data.name());
        entity.setDescription(data.description());
        return entity;
    }

    public AcademicPeriod toInput(AcademicPeriodDto data) {
        if (data == null) {
            return null;
        }
        AcademicPeriod entity = new AcademicPeriod();
        entity.setYear(data.year());
        entity.setSemester(data.semester());
        return entity;
    }

    public StudyPlan toInput(StudyPlanDto data) {
        if (data == null) {
            return null;
        }
        StudyPlan entity = new StudyPlan();
        entity.setVersion(data.version());
        entity.setStatus(data.status());
        return entity;
    }

    public Teacher toInput(TeacherDto data) {
        if (data == null) {
            return null;
        }
        Teacher entity = new Teacher();
        entity.setUser(inputUser(data.user()));
        entity.setProfessionalTitle(data.professionalTitle());
        entity.setAcademicDegree(data.academicDegree());
        return entity;
    }

    public Student toInput(StudentDto data) {
        if (data == null) {
            return null;
        }
        Student entity = new Student();
        entity.setUser(inputUser(data.user()));
        return entity;
    }

    public Course toInput(CourseDto data) {
        if (data == null) {
            return null;
        }
        Course entity = new Course();
        entity.setCode(data.code());
        entity.setName(data.name());
        entity.setSemester(data.semester());
        entity.setTheoryHours(data.theoryHours());
        entity.setExerciseHours(data.exerciseHours());
        entity.setLaboratoryHours(data.laboratoryHours());
        entity.setSct(data.sct());
        return entity;
    }

    public Section toInput(SectionDto data) {
        if (data == null) {
            return null;
        }
        Section entity = new Section();
        entity.setCapacity(data.capacity());
        if (data.scheduleBlocks() == null) {
            entity.setScheduleBlocks(null);
        } else {
            entity.setScheduleBlocks(new ArrayList<>());
            for (SectionScheduleBlockDto block : data.scheduleBlocks()) {
                if (block == null) {
                    entity.getScheduleBlocks().add(null);
                } else {
                    SectionScheduleBlock copy = new SectionScheduleBlock();
                    copy.setDayOfWeek(block.dayOfWeek());
                    copy.setModule(block.module());
                    entity.getScheduleBlocks().add(copy);
                }
            }
        }
        return entity;
    }
}
