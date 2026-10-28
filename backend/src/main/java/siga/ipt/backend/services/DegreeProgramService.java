package siga.ipt.backend.services;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import siga.ipt.backend.dtos.DegreeProgramDto;
import siga.ipt.backend.mappers.AcademicMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.entitys.DegreeProgram;
import siga.ipt.backend.entitys.enums.ActiveStatus;
import siga.ipt.backend.repositories.DegreeProgramRepository;
import siga.ipt.backend.repositories.StudyPlanRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DegreeProgramService {

    private final AcademicMapper mapper;
    private final CurrentUserService currentUser;

    private final DegreeProgramRepository degreeProgramRepository;
    private final StudyPlanRepository studyPlanRepository;

    private List<DegreeProgram> getAllEntity() {
        return degreeProgramRepository.findAll();
    }

    private DegreeProgram getByIdEntity(Long id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "The degree program ID is required"
            );
        }

        return degreeProgramRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Degree program not found with ID: " + id
                ));
    }

    private DegreeProgram createEntity(DegreeProgram data) {
        validateData(data);

        String code = data.getCode().trim();

        if (degreeProgramRepository.existsByCode(code)) {
            throw new IllegalArgumentException(
                    "A degree program with this code already exists"
            );
        }

        DegreeProgram degreeProgram = new DegreeProgram();
        degreeProgram.setCode(code);
        degreeProgram.setName(data.getName().trim());
        degreeProgram.setDescription(data.getDescription().trim());
        degreeProgram.setDurationSemesters(4);
        degreeProgram.setStatus(ActiveStatus.ACTIVE);

        return degreeProgramRepository.save(degreeProgram);
    }

    private DegreeProgram updateEntity(Long id, DegreeProgram data) {
        DegreeProgram degreeProgram = degreeProgramRepository.findByIdForUpdate(requireId(id))
                .orElseThrow(() -> new EntityNotFoundException("Degree program not found with ID: " + id));
        validateData(data);

        String code = data.getCode().trim();

        if (degreeProgramRepository.existsByCodeAndIdNot(code, id)) {
            throw new IllegalArgumentException(
                    "Another degree program already uses this code"
            );
        }

        degreeProgram.setCode(code);
        degreeProgram.setName(data.getName().trim());
        degreeProgram.setDescription(data.getDescription().trim());
        degreeProgram.setDurationSemesters(4);

        return degreeProgramRepository.save(degreeProgram);
    }

    private DegreeProgram changeStatusEntity(Long id, ActiveStatus status) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "The degree program status is required"
            );
        }

        DegreeProgram degreeProgram = degreeProgramRepository.findByIdForUpdate(requireId(id))
                .orElseThrow(() -> new EntityNotFoundException("Degree program not found with ID: " + id));
        degreeProgram.setStatus(status);

        return degreeProgramRepository.save(degreeProgram);
    }

    private void deleteEntity(Long id) {
        DegreeProgram degreeProgram = degreeProgramRepository.findByIdForUpdate(requireId(id))
                .orElseThrow(() -> new EntityNotFoundException("Degree program not found with ID: " + id));

        if (studyPlanRepository.existsByDegreeProgramId(id)) {
            throw new IllegalStateException(
                    "A degree program with associated study plans or students "
                            + "cannot be deleted; change its status instead"
            );
        }

        degreeProgramRepository.delete(degreeProgram);
    }

    private void validateData(DegreeProgram data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Degree program data is required"
            );
        }

        validateText(data.getCode(), "Code", 30);
        validateText(data.getName(), "Name", 200);
        validateText(data.getDescription(), "Description", 2000);
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

    public List<DegreeProgramDto> getAll() {
        currentUser.requireAcademicUser();
        return getAllEntity().stream().map(mapper::toDto).toList();
    }

    public DegreeProgramDto getById(Long id) {
        currentUser.requireAcademicUser();
        return mapper.toDto(getByIdEntity(id));
    }

    @Transactional
    public DegreeProgramDto create(DegreeProgramDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(createEntity(mapper.toInput(data)));
    }

    @Transactional
    public DegreeProgramDto update(Long id, DegreeProgramDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(updateEntity(id, mapper.toInput(data)));
    }

    @Transactional
    public DegreeProgramDto changeStatus(Long id, DegreeProgramDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(changeStatusEntity(id, data == null ? null : data.status()));
    }

    @Transactional
    public void delete(Long id) {
        currentUser.requireRole("ADMIN");
        deleteEntity(id);
    }

    private Long requireId(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("The degree program ID is required");
        }
        return id;
    }
}
