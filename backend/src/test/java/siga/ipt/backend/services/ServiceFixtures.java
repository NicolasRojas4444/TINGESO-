package siga.ipt.backend.services;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import org.springframework.test.util.ReflectionTestUtils;
import siga.ipt.backend.entitys.AcademicPeriod;
import siga.ipt.backend.entitys.AcademicRecord;
import siga.ipt.backend.entitys.BaseEntity;
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
import siga.ipt.backend.entitys.enums.ActiveStatus;
import siga.ipt.backend.entitys.enums.GradeResult;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.entitys.enums.PlanStatus;
import siga.ipt.backend.entitys.enums.RegistrationStatus;
import siga.ipt.backend.entitys.enums.StudentStatus;

/** In-memory fixtures only: no Spring context, database, passwords or startup data. */
final class ServiceFixtures {

    private ServiceFixtures() {}

    static <T extends BaseEntity> T identified(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    static DegreeProgram program() {
        DegreeProgram entity = identified(new DegreeProgram(), 1);
        entity.setCode("TAP");
        entity.setName("Software Development");
        entity.setDescription("Four-semester program");
        entity.setDurationSemesters(4);
        entity.setStatus(ActiveStatus.ACTIVE);
        return entity;
    }

    static StudyPlan plan() {
        StudyPlan entity = identified(new StudyPlan(), 1);
        entity.setDegreeProgram(program());
        entity.setVersion("2026.1");
        entity.setStatus(PlanStatus.CURRENT);
        return entity;
    }

    static User user(String subject) {
        User entity = identified(new User(), 1);
        entity.setKeycloakSubject(subject);
        entity.setRun("12345678-5");
        entity.setFullName("Test Person");
        entity.setEmail("person@example.test");
        return entity;
    }

    static Student student() {
        Student entity = identified(new Student(), 1);
        entity.setUser(user("student-subject"));
        entity.setStudyPlan(plan());
        entity.setAcademicStatus(StudentStatus.REGULAR);
        return entity;
    }

    static Teacher teacher() {
        Teacher entity = identified(new Teacher(), 1);
        entity.setUser(user("teacher-subject"));
        entity.setProfessionalTitle("Engineer");
        entity.setAcademicDegree("Bachelor");
        entity.setStatus(ActiveStatus.ACTIVE);
        return entity;
    }

    static Course course(long id, int semester) {
        Course entity = identified(new Course(), id);
        entity.setStudyPlan(plan());
        entity.setCode("TAP" + id);
        entity.setName("Course " + id);
        entity.setSemester(semester);
        entity.setTheoryHours(2);
        entity.setExerciseHours(0);
        entity.setLaboratoryHours(0);
        entity.setSct(4);
        return entity;
    }

    static AcademicPeriod period() {
        AcademicPeriod entity = identified(new AcademicPeriod(), 1);
        entity.setYear(2027);
        entity.setSemester(1);
        entity.setStatus(PeriodStatus.OPEN);
        return entity;
    }

    static SectionScheduleBlock block(DayOfWeek day, Integer module) {
        SectionScheduleBlock entity = new SectionScheduleBlock();
        entity.setDayOfWeek(day);
        entity.setModule(module);
        return entity;
    }

    static Section section(long id, Course course) {
        Section entity = identified(new Section(), id);
        entity.setCourse(course);
        entity.setAcademicPeriod(period());
        entity.setTeacher(teacher());
        entity.setCapacity(30);
        entity.setScheduleBlocks(new ArrayList<>());
        SectionScheduleBlock block = block(DayOfWeek.MONDAY, (int) id);
        block.setSection(entity);
        entity.getScheduleBlocks().add(block);
        return entity;
    }

    static CourseRegistration registration(long id, Section section) {
        CourseRegistration entity = identified(new CourseRegistration(), id);
        entity.setStudent(student());
        entity.setSection(section);
        entity.setStatus(RegistrationStatus.ACTIVE);
        entity.setRegisteredAt(Instant.parse("2027-01-01T10:00:00Z"));
        return entity;
    }

    static FinalGrade grade(CourseRegistration registration, String value) {
        FinalGrade entity = identified(new FinalGrade(), registration.getId());
        entity.setRegistration(registration);
        entity.setValue(new BigDecimal(value));
        return entity;
    }

    static AcademicRecord history(CourseRegistration registration, GradeResult result) {
        AcademicRecord entity = identified(new AcademicRecord(), registration.getId());
        entity.setRegistration(registration);
        entity.setCourseCode(registration.getSection().getCourse().getCode());
        entity.setCourseName(registration.getSection().getCourse().getName());
        entity.setResult(result);
        entity.setFinalGrade(new BigDecimal(result == GradeResult.PASSED ? "5.0" : "3.0"));
        return entity;
    }
}
