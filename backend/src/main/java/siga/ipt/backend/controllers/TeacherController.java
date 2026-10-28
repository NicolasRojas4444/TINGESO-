package siga.ipt.backend.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import siga.ipt.backend.dtos.TeacherDto;
import siga.ipt.backend.services.TeacherService;
import siga.ipt.backend.services.CurrentUserService;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {
    private final TeacherService service;
    private final CurrentUserService currentUser;
    @GetMapping("/me") public TeacherDto getMe() { return service.getById(currentUser.teacherId()); }
    @GetMapping public List<TeacherDto> getAll() { return service.getAll(); }
    @GetMapping("/{id}") public TeacherDto getById(@PathVariable Long id) { return service.getById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public TeacherDto create(@RequestBody TeacherDto data) { return service.create(data); }
    @PutMapping("/{id}")
    public TeacherDto update(@PathVariable Long id, @RequestBody TeacherDto data) { return service.update(id, data); }
    @PutMapping("/{id}/status")
    public TeacherDto status(@PathVariable Long id, @RequestBody TeacherDto data) { return service.changeStatus(id, data); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
