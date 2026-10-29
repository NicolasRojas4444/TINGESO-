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
import siga.ipt.backend.dtos.CourseDto;
import siga.ipt.backend.services.CourseService;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService service;

    @GetMapping
    public List<CourseDto> getAll() {
        return service.getAll();
    }

    @GetMapping("/by-study-plan/{id}")
    public List<CourseDto> byStudyPlan(@PathVariable Long id) {
        return service.getByStudyPlan(id);
    }

    @GetMapping("/{id}")
    public CourseDto getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/{id}/prerequisites")
    public List<CourseDto> prerequisites(@PathVariable Long id) {
        return service.getPrerequisites(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CourseDto create(@RequestBody CourseDto data) {
        return service.create(data.studyPlanId(), data);
    }

    @PutMapping("/{id}")
    public CourseDto update(
            @PathVariable Long id,
            @RequestBody CourseDto data
    ) {
        return service.update(id, data);
    }

    @PutMapping("/{id}/prerequisites")
    public List<CourseDto> prerequisites(
            @PathVariable Long id,
            @RequestBody List<Long> prerequisiteIds
    ) {
        return service.setPrerequisites(id, prerequisiteIds);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
