package com.enrollgate.enrollment.service;

import java.time.LocalDateTime;

public record EnrollmentResult(Long enrollmentId, LocalDateTime enrolledAt) {
}
