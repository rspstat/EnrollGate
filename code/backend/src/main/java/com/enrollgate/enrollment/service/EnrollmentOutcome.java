package com.enrollgate.enrollment.service;

import java.time.LocalDateTime;

public sealed interface EnrollmentOutcome {

    record Enrolled(Long enrollmentId, LocalDateTime enrolledAt) implements EnrollmentOutcome {
    }

    record Queued(long queuePosition, long estimatedWaitSeconds) implements EnrollmentOutcome {
    }
}
