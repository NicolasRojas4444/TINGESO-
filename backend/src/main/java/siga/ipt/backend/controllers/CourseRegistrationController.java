package siga.ipt.backend.controllers;

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
    public List<CourseRegistrationDto> replaceSelection(
            @RequestParam Long studentId,
            @RequestParam Long periodId,
            @RequestBody CourseSelectionRequest request
    ) {
        return service.replaceSelection(studentId, periodId, request);
    }

    @DeleteMapping("/{id}")
    public CourseRegistrationDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
