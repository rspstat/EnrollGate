package com.enrollgate.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.enrollgate.ai.service.BotDetectionScorer.BotRequestFeatures;
import com.enrollgate.ai.service.BotDetectionScorer.ScoringResult;
import org.junit.jupiter.api.Test;

class HeuristicBotDetectionScorerTest {

    private final HeuristicBotDetectionScorer scorer = new HeuristicBotDetectionScorer();

    @Test
    void scoresLow_forNormalPattern() {
        ScoringResult result = scorer.score(
                new BotRequestFeatures(1L, 100L, 15.0, 1, false, "ENROLLED"));

        assertThat(result.suspicionScore()).isEqualTo(0.0);
        assertThat(result.actionTaken()).isEqualTo("LOGGED");
    }

    @Test
    void flags_whenAllThreeSignalsPresent() {
        ScoringResult result = scorer.score(
                new BotRequestFeatures(1L, 100L, 0.1, 10, true, "ALREADY_ENROLLED"));

        assertThat(result.suspicionScore()).isEqualTo(1.0);
        assertThat(result.actionTaken()).isEqualTo("FLAGGED");
    }

    @Test
    void doesNotFlag_onSingleWeakSignal() {
        ScoringResult result = scorer.score(
                new BotRequestFeatures(1L, 100L, null, 1, true, "ENROLLED"));

        assertThat(result.suspicionScore()).isEqualTo(0.3);
        assertThat(result.actionTaken()).isEqualTo("LOGGED");
    }

    @Test
    void firstRequestEver_hasNullInterval_treatedAsNotSuspicious() {
        ScoringResult result = scorer.score(
                new BotRequestFeatures(1L, 100L, null, 1, false, "ENROLLED"));

        assertThat(result.suspicionScore()).isEqualTo(0.0);
        assertThat(result.actionTaken()).isEqualTo("LOGGED");
    }
}
