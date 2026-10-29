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
import siga.ipt.backend.dtos.DegreeProgramDto;
import siga.ipt.backend.services.DegreeProgramService;

@RestController
@RequestMapping("/api/degree-programs")
@RequiredArgsConstructor
public class DegreeProgramController {

    private final DegreeProgramService service;

    @GetMapping
    public List<DegreeProgramDto> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public DegreeProgramDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DegreeProgramDto create(@RequestBody DegreeProgramDto data) {
        return service.create(data);
    }

    @PutMapping("/{id}")
    public DegreeProgramDto update(
            @PathVariable Long id,
            @RequestBody DegreeProgramDto data
    ) {
        return service.update(id, data);
    }

    @PutMapping("/{id}/status")
    public DegreeProgramDto status(
            @PathVariable Long id,
            @RequestBody DegreeProgramDto data
    ) {
        return service.changeStatus(id, data);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
