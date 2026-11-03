package siga.ipt.backend.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import siga.ipt.backend.dtos.CourseRegistrationDto;
import siga.ipt.backend.dtos.CourseSelectionRequest;
import siga.ipt.backend.dtos.SectionDto;
import siga.ipt.backend.services.CourseRegistrationService;

@RestController
@Tag(name = "Registrations", description = "STUDENT changes only their own registrations. ADMIN may consult students; responsible teachers may consult their section rosters. Enrollment is supplied outside this application.")
@RequestMapping("/api/registrations")
@RequiredArgsConstructor
public class CourseRegistrationController {

    private final CourseRegistrationService service;

    @GetMapping("/by-student/{id}")
    public List<CourseRegistrationDto> byStudent(@PathVariable Long id) {
        return service.getByStudent(id);
    }

    @GetMapping("/by-section/{id}")
    public List<CourseRegistrationDto> bySection(@PathVariable Long id) {
        return service.getBySection(id);
    }

    @GetMapping("/available")
    public List<SectionDto> available(
            @RequestParam Long studentId,
            @RequestParam Long periodId
    ) {
        return service.getAvailableSections(studentId, periodId);
    }

    @PostMapping
    @Operation(summary = "Register selected sections",
            description = "Own STUDENT profile only, REGULAR and enrolled in an OPEN period. Validates plan, prerequisites, seats, schedules, earlier-semester priority and a final load of 3–6 courses (exception when fewer than three remain). The body is an array of objects containing sectionId.")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CourseRegistrationDto> register(
            @RequestParam Long studentId,
            @RequestParam Long periodId,
            @RequestBody List<CourseRegistrationDto> selection
    ) {
        return service.register(studentId, periodId, selection);
    }

    @GetMapping("/replacement-options")
    public List<SectionDto> replacementOptions(
            @RequestParam Long studentId,
            @RequestParam Long periodId
    ) {
        return service.getReplacementOptions(studentId, periodId);
    }

    @PutMapping
    @Operation(summary = "Replace the complete registration selection",
            description = "Own STUDENT profile only. Submit the complete desired sectionIds list. Validation is atomic; unchanged registrations keep their dates and grades, removed ones are cancelled and lose provisional grades.")
    public List<CourseRegistrationDto> replaceSelection(
            @RequestParam Long studentId,
            @RequestParam Long periodId,
            @RequestBody CourseSelectionRequest request
    ) {
        return service.replaceSelection(studentId, periodId, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a registration",
            description = "Owning STUDENT only, while the period is OPEN. Validates remaining load and earlier-semester priority, releases the seat and removes any provisional final grade.")
    public CourseRegistrationDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
