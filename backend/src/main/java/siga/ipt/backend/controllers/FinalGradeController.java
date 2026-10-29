package siga.ipt.backend.controllers;

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
    @ResponseStatus(HttpStatus.CREATED)
    public FinalGradeDto create(
            @PathVariable Long id,
            @RequestBody FinalGradeDto data
    ) {
        return service.create(id, data);
    }

    @PutMapping("/by-registration/{id}")
    public FinalGradeDto update(
            @PathVariable Long id,
            @RequestBody FinalGradeDto data
    ) {
        return service.update(id, data);
    }
}
