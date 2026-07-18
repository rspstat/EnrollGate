package com.enrollgate.course.controller;

import com.enrollgate.course.service.CourseCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/courses")
@RequiredArgsConstructor
public class AdminCourseController {

    private final CourseCommandService courseCommandService;

    @PostMapping
    public ResponseEntity<CreateCourseResponse> create(@Valid @RequestBody CreateCourseRequest request) {
        Long courseId = courseCommandService.create(
                request.courseCode(), request.name(), request.professorName(), request.department(),
                request.credit(), request.capacity(), request.semester(),
                request.enrollmentStartAt(), request.enrollmentEndAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(new CreateCourseResponse(courseId));
    }

    @PatchMapping("/{courseId}")
    public ResponseEntity<Void> update(@PathVariable Long courseId, @Valid @RequestBody UpdateCourseRequest request) {
        courseCommandService.update(courseId, request.name(), request.professorName(), request.department(),
                request.credit(), request.capacity(), request.enrollmentStartAt(), request.enrollmentEndAt());
        return ResponseEntity.noContent().build();
    }
}
