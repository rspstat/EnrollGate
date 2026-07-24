package com.enrollgate.course.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record CreateCourseRequest(
        @NotBlank String courseCode,
        @NotBlank String name,
        @NotBlank String professorName,
        @NotBlank String department,
        @NotNull @Positive Integer credit,
        @NotNull @Positive Integer capacity,
        @NotBlank String semester,
        @NotNull LocalDateTime enrollmentStartAt,
        @NotNull LocalDateTime enrollmentEndAt
) {
}
