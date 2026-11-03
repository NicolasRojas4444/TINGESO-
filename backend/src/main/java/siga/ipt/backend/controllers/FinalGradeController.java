package siga.ipt.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import siga.ipt.backend.dtos.FinalGradeDto;
import siga.ipt.backend.services.FinalGradeService;

@RestController
@Tag(name = "Final grades", description = "Only the responsible TEACHER may write grades for active registrations in OPEN periods. ADMIN can consult grades; STUDENT access is restricted to their own closed-period results.")
@RequestMapping("/api/final-grades")
@RequiredArgsConstructor
public class FinalGradeController {

    private final FinalGradeService service;

    @GetMapping("/by-registration/{id}")
    public FinalGradeDto byRegistration(@PathVariable Long id) {
        return service.getByRegistration(id);
    }

    @GetMapping("/by-section/{id}")
    public List<FinalGradeDto> bySection(@PathVariable Long id) {
        return service.getBySection(id);
    }

    @PostMapping("/by-registration/{id}")
    @Operation(summary = "Record a final grade",
            description = "Responsible TEACHER only. Accepts value between 1.0 and 7.0 with at most one decimal. A value of at least 4.0 is PASSED. One grade per active registration; period must be OPEN.")
    @ResponseStatus(HttpStatus.CREATED)
    public FinalGradeDto create(
            @PathVariable Long id,
            @RequestBody FinalGradeDto data
    ) {
        return service.create(id, data);
    }

    @PutMapping("/by-registration/{id}")
    @Operation(summary = "Update a final grade",
            description = "Responsible TEACHER only. Same range/precision rules as creation; the period must still be OPEN.")
    public FinalGradeDto update(
            @PathVariable Long id,
            @RequestBody FinalGradeDto data
    ) {
        return service.update(id, data);
    }
}
