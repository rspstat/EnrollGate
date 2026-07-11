package com.enrollgate.enrollment.controller;

import com.enrollgate.enrollment.service.EnrollmentResult;
import java.time.LocalDateTime;

public record EnrollResponse(String status, Long enrollmentId, LocalDateTime enrolledAt) {

    public static EnrollResponse from(EnrollmentResult result) {
        return new EnrollResponse("ENROLLED", result.enrollmentId(), result.enrolledAt());
    }
}
