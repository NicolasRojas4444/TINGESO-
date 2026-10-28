package siga.ipt.backend.controllers;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import siga.ipt.backend.dtos.*;
import siga.ipt.backend.services.*;

@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {
    private final StudentService service;
    private final AcademicHistoryService history;
    private final CurrentUserService currentUser;

    @GetMapping public List<StudentDto> getAll() { return service.getAll(); }
    @GetMapping("/me") public StudentDto getMe() { return service.getById(currentUser.studentId()); }
    @GetMapping("/{id}") public StudentDto getById(@PathVariable Long id) { return service.getById(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public StudentDto create(@RequestBody StudentDto data) { return service.create(data.degreeProgramId(), data); }
    @PutMapping("/{id}")
    public StudentDto update(@PathVariable Long id, @RequestBody StudentDto data) { return service.update(id, data); }
    @PutMapping("/{id}/status")
    public StudentDto status(@PathVariable Long id, @RequestBody StudentDto data) { return service.changeStatus(id, data); }
    @PutMapping("/{id}/study-plan")
    public StudentDto studyPlan(@PathVariable Long id, @RequestBody StudentDto data) {
        return service.changeStudyPlan(id, data.studyPlanId());
    }
    @GetMapping("/{id}/history")
    public List<AcademicRecordDto> history(@PathVariable Long id) { return history.getByStudent(id); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
