package com.enrollgate.ai.service;

/**
 * 신청 요청 특징을 받아 봇/매크로 의심 점수를 매긴다. 정원 동시성 제어 전략과 같은 패턴(설정으로 전환 가능한
 * 두 구현)을 따른다 — {@code ai.scorer=heuristic}(기본, 외부 의존성 없음) | {@code isolation-forest}(Python
 * scikit-learn 서비스 호출, 장애 시 heuristic으로 자동 대체).
 */
public interface BotDetectionScorer {

    ScoringResult score(BotRequestFeatures features);

    record BotRequestFeatures(
            Long userId,
            Long courseId,
            Double intervalSeconds,
            int repeatedCount1Min,
            boolean userAgentSuspicious,
            String outcome
    ) {
    }

    record ScoringResult(double suspicionScore, String actionTaken) {
    }
}
