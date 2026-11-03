package siga.ipt.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import siga.ipt.backend.dtos.AcademicPeriodDto;
import siga.ipt.backend.services.AcademicClosingService;
import siga.ipt.backend.services.AcademicPeriodService;

@RestController
@Tag(name = "Academic periods", description = "Academic period consultation and ADMIN-only creation/definitive closing.")
@RequestMapping("/api/academic-periods")
@RequiredArgsConstructor
public class AcademicPeriodController {

    private final AcademicPeriodService service;
    private final AcademicClosingService closing;

    @GetMapping
    public List<AcademicPeriodDto> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public AcademicPeriodDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AcademicPeriodDto create(@RequestBody AcademicPeriodDto data) {
        return service.create(data);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Close an academic period",
            description = "ADMIN only. Requires an OPEN period with final grades for all active registrations. Consolidates history and determines graduation or elimination. Closed results cannot be edited.")
    @ApiResponse(responseCode = "200", description = "Period closed and academic history consolidated",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = AcademicPeriodDto.class)))
    @ApiResponse(responseCode = "409", description = "Period is already closed or final grades are missing",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class), examples = @ExampleObject(
                    value = "{\"status\":409,\"detail\":\"Final grades are missing\",\"code\":\"FINAL_GRADES_MISSING\",\"sectionIds\":[4,8]}")))
    public AcademicPeriodDto close(@PathVariable Long id) {
        return closing.close(id);
    }
}
