package com.enrollgate.ai.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * code/ai-model(FastAPI + scikit-learn IsolationForest)에 HTTP로 점수를 요청하는 강화 스코어러.
 * PRD Open Question("AI 이상 탐지 모델의 구체적 알고리즘")에 대한 답으로 Isolation Forest를 채택했다.
 * 이 서비스가 꺼져 있거나 응답에 실패하면 신청 처리에 영향이 없도록 {@link HeuristicBotDetectionScorer}로
 * 즉시 대체한다 — 봇 탐지는 어디까지나 부가 기능이라 이 경로가 신청 자체를 막아서는 안 된다.
 */
@Service
@ConditionalOnProperty(name = "ai.scorer", havingValue = "isolation-forest")
public class PythonModelBotDetectionScorer implements BotDetectionScorer {

    private static final Logger log = LoggerFactory.getLogger(PythonModelBotDetectionScorer.class);

    private final RestTemplate restTemplate;
    private final HeuristicBotDetectionScorer fallback = new HeuristicBotDetectionScorer();
    private final String scoringUrl;

    public PythonModelBotDetectionScorer(RestTemplateBuilder restTemplateBuilder,
                                          @Value("${ai.scoring-service-url:http://localhost:8000/score}") String scoringUrl) {
        // 클래스패스에 Apache HttpClient/OkHttp/Jetty가 없으면 Spring Boot는 RestTemplateBuilder.build()에서
        // JDK java.net.http.HttpClient 기반 팩토리를 고른다. 이 클라이언트는 평문 HTTP에도 기본적으로
        // h2c(HTTP/2 cleartext) 업그레이드를 시도하는데, uvicorn(h11)은 이 업그레이드 요청을 이해하지 못해
        // "Unsupported upgrade request"로 커넥션을 오염시키고 이후 요청까지 422로 깨뜨린다.
        // HttpURLConnection 기반의 구식 SimpleClientHttpRequestFactory는 HTTP/1.1만 쓰므로 이 문제가 없다.
        this.restTemplate = restTemplateBuilder.requestFactory(SimpleClientHttpRequestFactory::new).build();
        this.scoringUrl = scoringUrl;
    }

    @Override
    public ScoringResult score(BotRequestFeatures features) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "interval_seconds", features.intervalSeconds() == null ? -1.0 : features.intervalSeconds(),
                    "repeated_count_1min", features.repeatedCount1Min(),
                    "user_agent_suspicious", features.userAgentSuspicious() ? 1 : 0
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(scoringUrl, request, Map.class);
            double suspicionScore = ((Number) response.get("suspicion_score")).doubleValue();
            // 임계값을 하드코딩하기보다 모델 자신의 이상치 판정(is_anomaly)을 그대로 따른다 — 점수 스케일은
            // 모델/데이터에 따라 달라지므로, 별도 threshold를 여기서 다시 매기면 모델의 보정과 어긋날 수 있다.
            boolean isAnomaly = Boolean.TRUE.equals(response.get("is_anomaly"));
            return new ScoringResult(suspicionScore, isAnomaly ? "FLAGGED" : "LOGGED");
        } catch (RestClientException | NullPointerException | ClassCastException ex) {
            log.warn("AI 스코어링 서비스({}) 호출 실패, 휴리스틱 점수로 대체: {}", scoringUrl, ex.getMessage());
            return fallback.score(features);
        }
    }
}
