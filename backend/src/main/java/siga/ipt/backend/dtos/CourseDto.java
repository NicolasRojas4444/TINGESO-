package siga.ipt.backend.dtos;

public record CourseDto(
        Long id,
        Long studyPlanId,
        String code,
        String name,
        Integer semester,
        Integer theoryHours,
        Integer exerciseHours,
        Integer laboratoryHours,
        Integer sct
) {
}
