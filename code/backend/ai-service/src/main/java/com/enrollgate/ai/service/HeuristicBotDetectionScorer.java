package com.enrollgate.ai.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 외부 의존성 없는 규칙 기반 기본 스코어러. 매우 짧은 요청 간격 + 짧은 시간 내 반복 신청 + 의심스러운
 * User-Agent 세 가지 신호를 가중합해 점수를 매긴다. PRD 6.4의 탐지 피처(요청 간격, 반복 패턴, UA 이상치)를
 * 그대로 반영한다.
 */
@Service
@ConditionalOnProperty(name = "ai.scorer", havingValue = "heuristic", matchIfMissing = true)
public class HeuristicBotDetectionScorer implements BotDetectionScorer {

    private static final double FLAG_THRESHOLD = 0.6;
    private static final double VERY_SHORT_INTERVAL_SECONDS = 1.0;
    private static final int REPEATED_COUNT_THRESHOLD = 5;

    @Override
    public ScoringResult score(BotRequestFeatures features) {
        double score = 0.0;
        if (features.intervalSeconds() != null && features.intervalSeconds() < VERY_SHORT_INTERVAL_SECONDS) {
            score += 0.4;
        }
        if (features.repeatedCount1Min() >= REPEATED_COUNT_THRESHOLD) {
            score += 0.3;
        }
        if (features.userAgentSuspicious()) {
            score += 0.3;
        }
        score = Math.min(score, 1.0);
        return new ScoringResult(score, score >= FLAG_THRESHOLD ? "FLAGGED" : "LOGGED");
    }
}
