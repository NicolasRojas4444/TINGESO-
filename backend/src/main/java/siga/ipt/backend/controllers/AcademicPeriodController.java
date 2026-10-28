package siga.ipt.backend.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import siga.ipt.backend.dtos.AcademicPeriodDto;
import siga.ipt.backend.services.AcademicPeriodService;
import siga.ipt.backend.services.AcademicClosingService;

@RestController
@RequestMapping("/api/academic-periods")
@RequiredArgsConstructor
public class AcademicPeriodController {
    private final AcademicPeriodService service;
    private final AcademicClosingService closing;
    @GetMapping public List<AcademicPeriodDto> getAll() { return service.getAll(); }
    @GetMapping("/{id}") public AcademicPeriodDto getById(@PathVariable Long id) { return service.getById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AcademicPeriodDto create(@RequestBody AcademicPeriodDto data) { return service.create(data); }
    @PostMapping("/{id}/close") public AcademicPeriodDto close(@PathVariable Long id) { return closing.close(id); }
}
