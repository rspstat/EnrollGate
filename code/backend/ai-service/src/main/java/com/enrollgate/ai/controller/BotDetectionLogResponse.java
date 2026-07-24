package com.enrollgate.ai.controller;

import com.enrollgate.ai.domain.BotDetectionLog;
import java.time.LocalDateTime;

public record BotDetectionLogResponse(
        Long id,
        Long userId,
        Long courseId,
        String requestFeatures,
        Double suspicionScore,
        String actionTaken,
        LocalDateTime createdAt
) {
    public static BotDetectionLogResponse from(BotDetectionLog log) {
        return new BotDetectionLogResponse(log.getId(), log.getUserId(), log.getCourseId(), log.getRequestFeatures(),
                log.getSuspicionScore(), log.getActionTaken(), log.getCreatedAt());
    }
}
