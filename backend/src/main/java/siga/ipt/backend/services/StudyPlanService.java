package siga.ipt.backend.services;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.dtos.StudyPlanDto;
import siga.ipt.backend.entitys.DegreeProgram;
import siga.ipt.backend.entitys.StudyPlan;
import siga.ipt.backend.entitys.enums.PlanStatus;
import siga.ipt.backend.mappers.AcademicMapper;
import siga.ipt.backend.repositories.CourseRepository;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.StudentRepository;
import siga.ipt.backend.repositories.StudyPlanRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyPlanService {

    private final AcademicMapper mapper;
    private final CurrentUserService currentUser;

    private final StudyPlanRepository studyPlanRepository;
    private final DegreeProgramRepository degreeProgramRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;

    public List<StudyPlanDto> getAll() {
        currentUser.requireAcademicUser();
        return getAllEntity().stream()
                .map(mapper::toDto)
                .toList();
    }

    public StudyPlanDto getById(Long id) {
        currentUser.requireAcademicUser();
        return mapper.toDto(getByIdEntity(id));
    }

    public List<StudyPlanDto> getByDegreeProgram(Long degreeProgramId) {
        currentUser.requireAcademicUser();
        return getByDegreeProgramEntity(degreeProgramId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    public StudyPlanDto create(Long degreeProgramId, StudyPlanDto data) {
        currentUser.requireRole("ADMIN");
        StudyPlan studyPlan = createEntity(degreeProgramId, mapper.toInput(data));
        return mapper.toDto(studyPlan);
    }

    @Transactional
    public StudyPlanDto update(Long id, StudyPlanDto data) {
        currentUser.requireRole("ADMIN");
        StudyPlan studyPlan = updateEntity(id, mapper.toInput(data));
        return mapper.toDto(studyPlan);
    }

    @Transactional
    public StudyPlanDto setCurrent(Long id) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(setCurrentEntity(id));
    }

    @Transactional
    public void delete(Long id) {
        currentUser.requireRole("ADMIN");
        deleteEntity(id);
    }

    private List<StudyPlan> getAllEntity() {
        return studyPlanRepository.findAll();
    }


    private StudyPlan getByIdEntity(Long id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "The study plan ID is required"
            );
        }
        return studyPlanRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Study plan not found with ID: " + id
                ));
    }


    private List<StudyPlan> getByDegreeProgramEntity(Long degreeProgramId) {
        if (degreeProgramId == null) {
            throw new IllegalArgumentException(
                    "The degree program ID is required"
            );
        }
        if (!degreeProgramRepository.existsById(degreeProgramId)) {
            throw new EntityNotFoundException(
                    "Degree program not found with ID: " + degreeProgramId
            );
        }
        return studyPlanRepository.findByDegreeProgramId(degreeProgramId);
    }


    private StudyPlan createEntity(Long degreeProgramId, StudyPlan data) {
        validateData(data);
        DegreeProgram degreeProgram = getDegreeProgramForUpdate(
                degreeProgramId
        );
        String version = data.getVersion().trim();
        if (studyPlanRepository.existsByDegreeProgramIdAndVersion(
                degreeProgramId, version)) {
            throw new IllegalArgumentException(
                    "This degree program already has a plan with this version"
            );
        }
        StudyPlan studyPlan = new StudyPlan();
        studyPlan.setDegreeProgram(degreeProgram);
        studyPlan.setVersion(version);
        studyPlan.setStatus(PlanStatus.NON_CURRENT);

        studyPlan = studyPlanRepository.save(studyPlan);

        if (data.getStatus() == PlanStatus.CURRENT) {
            return replaceCurrentPlan(studyPlan);
        }
        return studyPlan;
    }


    private StudyPlan updateEntity(Long id, StudyPlan data) {
        validateData(data);

        StudyPlan studyPlan = getForUpdate(id);
        Long degreeProgramId = studyPlan.getDegreeProgram().getId();

        String version = data.getVersion().trim();

        if (studyPlanRepository.existsByDegreeProgramIdAndVersionAndIdNot(
                degreeProgramId, version, id)) {
            throw new IllegalArgumentException(
                    "Another plan in this degree program uses this version"
            );
        }
        studyPlan.setVersion(version);
        return studyPlanRepository.save(studyPlan);
    }


    private StudyPlan setCurrentEntity(Long id) {
        StudyPlan studyPlan = getForUpdate(id);
        return replaceCurrentPlan(studyPlan);
    }


    private void deleteEntity(Long id) {
        StudyPlan studyPlan = getForUpdate(id);

        if (studentRepository.existsByStudyPlanId(id)) {
            throw new IllegalStateException(
                    "A study plan with associated students cannot be deleted"
            );
        }
        if (courseRepository.existsByStudyPlanId(id)) {
            throw new IllegalStateException(
                    "A study plan with associated courses cannot be deleted"
            );
        }
        studyPlanRepository.delete(studyPlan);
    }


    private StudyPlan replaceCurrentPlan(StudyPlan studyPlan) {
        Long degreeProgramId = studyPlan.getDegreeProgram().getId();

        List<StudyPlan> currentPlans =
                studyPlanRepository.findByDegreeProgramIdAndStatus(
                        degreeProgramId,
                        PlanStatus.CURRENT
                );
        for (StudyPlan currentPlan : currentPlans) {
            if (!currentPlan.getId().equals(studyPlan.getId())) {
                currentPlan.setStatus(PlanStatus.NON_CURRENT);
                studyPlanRepository.save(currentPlan);
            }
        }
        studyPlan.setStatus(PlanStatus.CURRENT);
        return studyPlanRepository.save(studyPlan);
    }


    private StudyPlan getForUpdate(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("The study plan ID is required");
        }

        Long degreeProgramId = studyPlanRepository.findDegreeProgramIdById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Study plan not found with ID: " + id
                ));

        getDegreeProgramForUpdate(degreeProgramId);
        return getByIdEntity(id);
    }

    private DegreeProgram getDegreeProgramForUpdate(Long id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "The degree program ID is required"
            );
        }
        return degreeProgramRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Degree program not found with ID: " + id
                ));
    }


    private void validateData(StudyPlan data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Study plan data is required"
            );
        }
        if (data.getVersion() == null || data.getVersion().isBlank()) {
            throw new IllegalArgumentException(
                    "The study plan version is required"
            );
        }
        if (data.getVersion().trim().length() > 30) {
            throw new IllegalArgumentException(
                    "The study plan version must not exceed 30 characters"
            );
        }
    }
}
