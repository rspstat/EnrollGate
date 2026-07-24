package com.enrollgate.enrollment.controller;

import com.enrollgate.enrollment.service.EnrollmentOutcome;
import java.time.LocalDateTime;

public record EnrollResponse(
        String status,
        Long enrollmentId,
        LocalDateTime enrolledAt,
        Long queuePosition,
        Long estimatedWaitSeconds,
        String queueStatusUrl
) {

    public static EnrollResponse enrolled(EnrollmentOutcome.Enrolled outcome) {
        return new EnrollResponse("ENROLLED", outcome.enrollmentId(), outcome.enrolledAt(), null, null, null);
    }

    public static EnrollResponse queued(Long courseId, EnrollmentOutcome.Queued outcome) {
        return new EnrollResponse("QUEUED", null, null, outcome.queuePosition(), outcome.estimatedWaitSeconds(),
                "/api/v1/courses/" + courseId + "/queue/status");
    }
}
