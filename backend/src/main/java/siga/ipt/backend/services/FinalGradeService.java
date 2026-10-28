package siga.ipt.backend.services;

import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.dtos.FinalGradeDto;
import siga.ipt.backend.entitys.AcademicPeriod;
import siga.ipt.backend.entitys.CourseRegistration;
import siga.ipt.backend.entitys.FinalGrade;
import siga.ipt.backend.entitys.Section;
import siga.ipt.backend.entitys.enums.GradeResult;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.entitys.enums.RegistrationStatus;
import siga.ipt.backend.mappers.AcademicMapper;
import siga.ipt.backend.repositories.AcademicPeriodRepository;
import siga.ipt.backend.repositories.CourseRegistrationRepository;
import siga.ipt.backend.repositories.FinalGradeRepository;
import siga.ipt.backend.repositories.SectionRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinalGradeService {

    private static final BigDecimal MINIMUM_GRADE = new BigDecimal("1.0");
    private static final BigDecimal MAXIMUM_GRADE = new BigDecimal("7.0");
    private static final BigDecimal PASSING_GRADE = new BigDecimal("4.0");

    private final FinalGradeRepository finalGradeRepository;
    private final CourseRegistrationRepository registrationRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final SectionRepository sectionRepository;
    private final AcademicMapper mapper;
    private final CurrentUserService currentUser;

    public FinalGradeDto getByRegistration(Long registrationId) {
        CourseRegistration registration = requireRegistration(registrationId);
        boolean allowed = currentUser.hasRole("ADMIN")
                || (currentUser.hasRole("TEACHER") && isResponsibleTeacher(registration))
                || (currentUser.hasRole("STUDENT")
                    && registration.getSection().getAcademicPeriod().getStatus() == PeriodStatus.CLOSED
                    && registration.getStudent().getId().equals(currentUser.studentId()));
        if (!allowed) {
            throw new AccessDeniedException("This grade is not accessible to the current user");
        }
        return toDto(requireGrade(registrationId));
    }

    public List<FinalGradeDto> getBySection(Long sectionId) {
        if (sectionId == null) {
            throw new IllegalArgumentException("The section ID is required");
        }
        Section section = sectionRepository.findById(sectionId)
                .orElseThrow(() -> new EntityNotFoundException("Section not found"));
        currentUser.requireTeacherAccess(section.getTeacher().getId());
        return finalGradeRepository.findByRegistrationSectionIdAndRegistrationStatus(
                sectionId, RegistrationStatus.ACTIVE).stream().map(this::toDto).toList();
    }

    @Transactional
    public FinalGradeDto create(Long registrationId, FinalGradeDto data) {
        currentUser.requireRole("TEACHER");
        BigDecimal value = validateValue(data);
        CourseRegistration registration = getEditableRegistration(registrationId);
        if (finalGradeRepository.existsByRegistrationId(registrationId)) {
            throw new IllegalStateException("This registration already has a final grade; use update");
        }
        FinalGrade grade = new FinalGrade();
        grade.setRegistration(registration);
        grade.setValue(value);
        return toDto(finalGradeRepository.save(grade));
    }

    @Transactional
    public FinalGradeDto update(Long registrationId, FinalGradeDto data) {
        currentUser.requireRole("TEACHER");
        BigDecimal value = validateValue(data);
        getEditableRegistration(registrationId);
        FinalGrade grade = requireGrade(registrationId);
        grade.setValue(value);
        return toDto(finalGradeRepository.save(grade));
    }

    private CourseRegistration getEditableRegistration(Long registrationId) {
        requireId(registrationId);
        Long periodId = registrationRepository.findAcademicPeriodIdById(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Registration not found"));
        AcademicPeriod period = academicPeriodRepository.findByIdForUpdate(periodId)
                .orElseThrow(() -> new EntityNotFoundException("Academic period not found"));
        CourseRegistration registration = requireRegistration(registrationId);
        if (!isResponsibleTeacher(registration)) {
            throw new AccessDeniedException("Only the responsible teacher can record final grades");
        }
        if (registration.getStatus() != RegistrationStatus.ACTIVE) {
            throw new IllegalStateException("Only active registrations can receive grades");
        }
        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new IllegalStateException("Final grades cannot change after period closing");
        }
        return registration;
    }

    private boolean isResponsibleTeacher(CourseRegistration registration) {
        return currentUser.subject().equals(registration.getSection().getTeacher().getUser().getKeycloakSubject());
    }

    private CourseRegistration requireRegistration(Long id) {
        requireId(id);
        return registrationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Registration not found"));
    }

    private FinalGrade requireGrade(Long registrationId) {
        return finalGradeRepository.findByRegistrationId(registrationId)
                .orElseThrow(() -> new EntityNotFoundException("Final grade not found for this registration"));
    }

    private BigDecimal validateValue(FinalGradeDto data) {
        if (data == null || data.value() == null) {
            throw new IllegalArgumentException("The final grade value is required");
        }
        BigDecimal value = data.value();
        if (value.compareTo(MINIMUM_GRADE) < 0 || value.compareTo(MAXIMUM_GRADE) > 0) {
            throw new IllegalArgumentException("The final grade must be between 1.0 and 7.0");
        }
        try {
            return value.setScale(1, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("The final grade must have at most one decimal place");
        }
    }

    private FinalGradeDto toDto(FinalGrade grade) {
        GradeResult result = grade.getValue().compareTo(PASSING_GRADE) >= 0
                ? GradeResult.PASSED : GradeResult.FAILED;
        return mapper.toDto(grade, result);
    }

    private void requireId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("The registration ID is required");
        }
    }
}
