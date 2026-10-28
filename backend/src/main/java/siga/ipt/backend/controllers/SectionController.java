package siga.ipt.backend.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import siga.ipt.backend.dtos.SectionDto;
import siga.ipt.backend.services.SectionService;

@RestController
@RequestMapping("/api/sections")
@RequiredArgsConstructor
public class SectionController {
    private final SectionService service;
    @GetMapping public List<SectionDto> getAll() { return service.getAll(); }
    @GetMapping("/by-period/{id}") public List<SectionDto> byPeriod(@PathVariable Long id) { return service.getByPeriod(id); }
    @GetMapping("/by-teacher/{id}") public List<SectionDto> byTeacher(@PathVariable Long id) { return service.getByTeacher(id); }
    @GetMapping("/{id}") public SectionDto getById(@PathVariable Long id) { return service.getById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public SectionDto create(@RequestBody SectionDto data) {
        return service.create(data.courseId(), data.academicPeriodId(), data.teacherId(), data);
    }
    @PutMapping("/{id}")
    public SectionDto update(@PathVariable Long id, @RequestBody SectionDto data) { return service.update(id, data.teacherId(), data); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
