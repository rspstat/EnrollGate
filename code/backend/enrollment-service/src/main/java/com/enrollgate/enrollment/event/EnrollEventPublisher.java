package com.enrollgate.enrollment.event;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 신청 시도마다(성공/대기열/거부 모두) 요청 특징을 Redis Stream으로 발행해 AI Service(ai-service 모듈)가
 * 비동기로 봇/매크로 의심 점수를 매기도록 한다. Architecture 문서: "Enrollment → AI Service: 비동기(메시지 큐)".
 * Kafka 대신 Redis Streams로 간소화했다(PRD/Architecture Open Question 결정).
 *
 * <p>이 클래스는 절대 예외를 밖으로 던지지 않는다 — 봇 탐지 파이프라인 장애가 신청 처리 자체를 막으면 안 된다는
 * 요구사항(PRD 6.4) 때문에 모든 실패를 흡수하고 경고 로그만 남긴다.
 */
@Component
@RequiredArgsConstructor
public class EnrollEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EnrollEventPublisher.class);
    private static final String STREAM_KEY = "enrollgate:enroll-events";
    private static final List<String> SUSPICIOUS_UA_MARKERS =
            List.of("bot", "curl", "python-requests", "scrapy", "wget", "httpclient", "okhttp", "java/");

    private final StringRedisTemplate redisTemplate;

    public void publish(Long userId, Long courseId, String userAgent, String outcome) {
        try {
            long nowMillis = System.currentTimeMillis();
            Long intervalMillis = computeIntervalMillis(userId, nowMillis);
            int repeatedCount = incrementAndGetRepeatedCount(userId, courseId);
            boolean uaSuspicious = isUserAgentSuspicious(userAgent);

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("userId", String.valueOf(userId));
            fields.put("courseId", String.valueOf(courseId));
            fields.put("outcome", outcome == null ? "UNKNOWN" : outcome);
            fields.put("intervalSeconds", intervalMillis == null ? "" : String.valueOf(intervalMillis / 1000.0));
            fields.put("repeatedCount1Min", String.valueOf(repeatedCount));
            fields.put("userAgentSuspicious", String.valueOf(uaSuspicious));
            fields.put("timestamp", String.valueOf(nowMillis));

            redisTemplate.opsForStream().add(STREAM_KEY, fields);
        } catch (Exception ex) {
            log.warn("봇 탐지 이벤트 발행 실패(신청 처리에는 영향 없음): userId={}, courseId={}, cause={}",
                    userId, courseId, ex.getMessage());
        }
    }

    private Long computeIntervalMillis(Long userId, long nowMillis) {
        String key = "enrollgate:bot-detect:last-request:" + userId;
        String previous = redisTemplate.opsForValue().getAndSet(key, String.valueOf(nowMillis));
        redisTemplate.expire(key, Duration.ofMinutes(10));
        return previous == null ? null : nowMillis - Long.parseLong(previous);
    }

    private int incrementAndGetRepeatedCount(Long userId, Long courseId) {
        String key = "enrollgate:bot-detect:req-count:" + userId + ":" + courseId;
        Long count = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofMinutes(1));
        return count == null ? 1 : count.intValue();
    }

    private boolean isUserAgentSuspicious(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return true;
        }
        String lower = userAgent.toLowerCase(Locale.ROOT);
        return SUSPICIOUS_UA_MARKERS.stream().anyMatch(lower::contains);
    }
}
