package com.enrollgate.enrollment.controller;

import com.enrollgate.enrollment.service.EnrollmentSummary;
import java.time.LocalDateTime;

public record EnrollmentSummaryResponse(
        Long enrollmentId,
        Long courseId,
        String courseCode,
        String courseName,
        String status,
        LocalDateTime enrolledAt,
        LocalDateTime cancelledAt
) {

    public static EnrollmentSummaryResponse from(EnrollmentSummary summary) {
        return new EnrollmentSummaryResponse(
                summary.enrollmentId(),
                summary.courseId(),
                summary.courseCode(),
                summary.courseName(),
                summary.status().name(),
                summary.enrolledAt(),
                summary.cancelledAt());
    }
}
