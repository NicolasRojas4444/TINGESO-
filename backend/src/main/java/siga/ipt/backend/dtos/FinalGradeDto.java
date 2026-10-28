package siga.ipt.backend.dtos;

import java.math.BigDecimal;
import siga.ipt.backend.entitys.enums.GradeResult;

public record FinalGradeDto(
        Long id,
        Long registrationId,
        Long studentId,
        Long sectionId,
        BigDecimal value,
        GradeResult result
) {
}
