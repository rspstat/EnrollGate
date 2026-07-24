package com.enrollgate.enrollment.service;

import com.enrollgate.enrollment.domain.Enrollment;
import com.enrollgate.enrollment.domain.EnrollmentStatus;
import java.time.LocalDateTime;

public record EnrollmentSummary(
        Long enrollmentId,
        Long courseId,
        String courseCode,
        String courseName,
        EnrollmentStatus status,
        LocalDateTime enrolledAt,
        LocalDateTime cancelledAt
) {

    public static EnrollmentSummary of(Enrollment enrollment, String courseCode, String courseName) {
        return new EnrollmentSummary(
                enrollment.getId(),
                enrollment.getCourseId(),
                courseCode,
                courseName,
                enrollment.getStatus(),
                enrollment.getEnrolledAt(),
                enrollment.getCancelledAt());
    }
}
