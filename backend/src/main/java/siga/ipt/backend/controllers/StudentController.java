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
import siga.ipt.backend.dtos.AcademicRecordDto;
import siga.ipt.backend.dtos.StudentDto;
import siga.ipt.backend.services.AcademicHistoryService;
import siga.ipt.backend.services.CurrentUserService;
import siga.ipt.backend.services.StudentService;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService service;
    private final AcademicHistoryService history;
    private final CurrentUserService currentUser;

    @GetMapping
    public List<StudentDto> getAll() {
        return service.getAll();
    }

    @GetMapping("/me")
    public StudentDto getMe() {
        return service.getById(currentUser.studentId());
    }

    @GetMapping("/{id}")
    public StudentDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/{id}/history")
    public List<AcademicRecordDto> history(@PathVariable Long id) {
        return history.getByStudent(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentDto create(@RequestBody StudentDto data) {
        return service.create(data.degreeProgramId(), data);
    }

    @PutMapping("/{id}")
    public StudentDto update(
            @PathVariable Long id,
            @RequestBody StudentDto data
    ) {
        return service.update(id, data);
    }

    @PutMapping("/{id}/status")
    public StudentDto status(
            @PathVariable Long id,
            @RequestBody StudentDto data
    ) {
        return service.changeStatus(id, data);
    }

    @PutMapping("/{id}/study-plan")
    public StudentDto studyPlan(
            @PathVariable Long id,
            @RequestBody StudentDto data
    ) {
        return service.changeStudyPlan(id, data.studyPlanId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
