package com.enrollgate.course.controller;

import com.enrollgate.course.service.CourseQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CourseController {

    private final CourseQueryService courseQueryService;

    @GetMapping("/api/v1/courses")
    public CourseListResponse list(@RequestParam(required = false) String semester,
                                    @RequestParam(required = false) String department) {
        return new CourseListResponse(
                courseQueryService.list(semester, department).stream().map(CourseResponse::from).toList());
    }

    @GetMapping("/api/v1/courses/{courseId}")
    public CourseResponse detail(@PathVariable Long courseId) {
        return CourseResponse.from(courseQueryService.detail(courseId));
    }
}
