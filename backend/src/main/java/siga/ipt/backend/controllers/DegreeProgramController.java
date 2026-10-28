package siga.ipt.backend.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import siga.ipt.backend.dtos.DegreeProgramDto;
import siga.ipt.backend.services.DegreeProgramService;

@RestController
@RequestMapping("/api/degree-programs")
@RequiredArgsConstructor
public class DegreeProgramController {
    private final DegreeProgramService service;
    @GetMapping public List<DegreeProgramDto> getAll() { return service.getAll(); }
    @GetMapping("/{id}") public DegreeProgramDto getById(@PathVariable Long id) { return service.getById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public DegreeProgramDto create(@RequestBody DegreeProgramDto data) { return service.create(data); }
    @PutMapping("/{id}")
    public DegreeProgramDto update(@PathVariable Long id, @RequestBody DegreeProgramDto data) { return service.update(id, data); }
    @PutMapping("/{id}/status")
    public DegreeProgramDto status(@PathVariable Long id, @RequestBody DegreeProgramDto data) { return service.changeStatus(id, data); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
