package com.enrollgate.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.enrollgate.ai.service.BotDetectionScorer.BotRequestFeatures;
import com.enrollgate.ai.service.BotDetectionScorer.ScoringResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class PythonModelBotDetectionScorerTest {

    private static final String SCORING_URL = "http://localhost:8000/score";

    private PythonModelBotDetectionScorer newScorerWithMockServer(MockRestServiceServer[] serverHolder) {
        PythonModelBotDetectionScorer scorer =
                new PythonModelBotDetectionScorer(new RestTemplateBuilder(), SCORING_URL);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(scorer, "restTemplate");
        serverHolder[0] = MockRestServiceServer.createServer(restTemplate);
        return scorer;
    }

    @Test
    void usesModelResponse_whenServiceReachable() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        PythonModelBotDetectionScorer scorer = newScorerWithMockServer(holder);
        holder[0].expect(requestTo(SCORING_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"suspicion_score\": 0.57, \"is_anomaly\": true}", MediaType.APPLICATION_JSON));

        ScoringResult result = scorer.score(new BotRequestFeatures(1L, 100L, 0.05, 20, true, "ENROLLED"));

        assertThat(result.suspicionScore()).isEqualTo(0.57);
        assertThat(result.actionTaken()).isEqualTo("FLAGGED");
    }

    @Test
    void fallsBackToHeuristic_whenServiceUnreachable() {
        MockRestServiceServer[] holder = new MockRestServiceServer[1];
        PythonModelBotDetectionScorer scorer = newScorerWithMockServer(holder);
        holder[0].expect(requestTo(SCORING_URL)).andRespond(withServerError());

        // 휴리스틱 스코어러와 동일한 강한 신호 조합 -> FLAGGED로 대체되어야 함
        ScoringResult result = scorer.score(new BotRequestFeatures(1L, 100L, 0.1, 10, true, "ENROLLED"));

        assertThat(result.actionTaken()).isEqualTo("FLAGGED");
    }
}
