package com.enrollgate.enrollment.service;

import java.time.LocalDateTime;

public record QueueStatusResult(String status, Long position, Long estimatedWaitSeconds, LocalDateTime confirmDeadline) {

    public static QueueStatusResult waiting(long position, long estimatedWaitSeconds) {
        return new QueueStatusResult("WAITING", position, estimatedWaitSeconds, null);
    }

    public static QueueStatusResult notified(LocalDateTime confirmDeadline) {
        return new QueueStatusResult("NOTIFIED", null, null, confirmDeadline);
    }
}
