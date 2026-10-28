package siga.ipt.backend.services;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import siga.ipt.backend.dtos.AcademicPeriodDto;
import siga.ipt.backend.mappers.AcademicMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import siga.ipt.backend.entitys.AcademicPeriod;
import siga.ipt.backend.entitys.enums.PeriodStatus;
import siga.ipt.backend.repositories.AcademicPeriodRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AcademicPeriodService {

    private final AcademicMapper mapper;
    private final CurrentUserService currentUser;

    private final AcademicPeriodRepository academicPeriodRepository;

    private List<AcademicPeriod> getAllEntity() {
        return academicPeriodRepository.findAll();
    }

    private AcademicPeriod getByIdEntity(Long id) {
        if (id == null) {
            throw new IllegalArgumentException(
                    "The academic period ID is required"
            );
        }

        return academicPeriodRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Academic period not found with ID: " + id
                ));
    }

    private AcademicPeriod createEntity(AcademicPeriod data) {
        validateData(data);

        if (academicPeriodRepository.existsByYearAndSemester(
                data.getYear(), data.getSemester())) {
            throw new IllegalArgumentException(
                    "An academic period with this year and semester already exists"
            );
        }

        AcademicPeriod academicPeriod = new AcademicPeriod();
        academicPeriod.setYear(data.getYear());
        academicPeriod.setSemester(data.getSemester());
        academicPeriod.setStatus(PeriodStatus.OPEN);

        return academicPeriodRepository.save(academicPeriod);
    }

    private void validateData(AcademicPeriod data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "Academic period data is required"
            );
        }

        if (data.getYear() == null || data.getYear() <= 0) {
            throw new IllegalArgumentException(
                    "The year must be a positive number"
            );
        }

        if (data.getSemester() == null
                || data.getSemester() < 1
                || data.getSemester() > 2) {
            throw new IllegalArgumentException(
                    "The semester must be either 1 or 2"
            );
        }
    }

    public List<AcademicPeriodDto> getAll() {
        currentUser.requireAcademicUser();
        return getAllEntity().stream().map(mapper::toDto).toList();
    }

    public AcademicPeriodDto getById(Long id) {
        currentUser.requireAcademicUser();
        return mapper.toDto(getByIdEntity(id));
    }

    @Transactional
    public AcademicPeriodDto create(AcademicPeriodDto data) {
        currentUser.requireRole("ADMIN");
        return mapper.toDto(createEntity(mapper.toInput(data)));
    }
}
