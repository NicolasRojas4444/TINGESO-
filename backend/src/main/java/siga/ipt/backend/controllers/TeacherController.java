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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import siga.ipt.backend.dtos.TeacherDto;
import siga.ipt.backend.services.CurrentUserService;
import siga.ipt.backend.services.TeacherService;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService service;
    private final CurrentUserService currentUser;

    @GetMapping
    public List<TeacherDto> getAll() {
        return service.getAll();
    }

    @GetMapping("/me")
    public TeacherDto getMe() {
        return service.getById(currentUser.teacherId());
    }

    @GetMapping("/{id}")
    public TeacherDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherDto create(@RequestBody TeacherDto data) {
        return service.create(data);
    }

    @PutMapping("/{id}")
    public TeacherDto update(
            @PathVariable Long id,
            @RequestBody TeacherDto data
    ) {
        return service.update(id, data);
    }

    @PutMapping("/{id}/status")
    public TeacherDto status(
            @PathVariable Long id,
            @RequestBody TeacherDto data
    ) {
        return service.changeStatus(id, data);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
