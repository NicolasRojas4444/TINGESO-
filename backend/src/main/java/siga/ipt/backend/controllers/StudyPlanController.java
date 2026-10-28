package siga.ipt.backend.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import siga.ipt.backend.dtos.StudyPlanDto;
import siga.ipt.backend.services.StudyPlanService;

@RestController
@RequestMapping("/api/study-plans")
@RequiredArgsConstructor
public class StudyPlanController {
    private final StudyPlanService service;
    @GetMapping public List<StudyPlanDto> getAll() { return service.getAll(); }
    @GetMapping("/by-degree-program/{id}")
    public List<StudyPlanDto> byDegreeProgram(@PathVariable Long id) { return service.getByDegreeProgram(id); }
    @GetMapping("/{id}") public StudyPlanDto getById(@PathVariable Long id) { return service.getById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public StudyPlanDto create(@RequestBody StudyPlanDto data) { return service.create(data.degreeProgramId(), data); }
    @PutMapping("/{id}")
    public StudyPlanDto update(@PathVariable Long id, @RequestBody StudyPlanDto data) { return service.update(id, data); }
    @PutMapping("/{id}/current") public StudyPlanDto current(@PathVariable Long id) { return service.setCurrent(id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
