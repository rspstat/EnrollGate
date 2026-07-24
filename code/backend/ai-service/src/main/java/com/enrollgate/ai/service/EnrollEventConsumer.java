package com.enrollgate.ai.service;

import com.enrollgate.ai.domain.BotDetectionLog;
import com.enrollgate.ai.repository.BotDetectionLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * enrollment-service가 발행한 신청 이벤트(Redis Stream)를 주기적으로 읽어 봇 탐지 점수를 매기고 로그로 남긴다.
 * Redis Consumer Group(XREADGROUP)을 써서, 이 인스턴스가 재시작되어도 이미 처리한 이벤트를 건너뛴다.
 */
@Component
@RequiredArgsConstructor
public class EnrollEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EnrollEventConsumer.class);
    private static final String STREAM_KEY = "enrollgate:enroll-events";
    private static final String CONSUMER_GROUP = "ai-service";
    private static final String CONSUMER_NAME = "ai-service-consumer-1";

    private final StringRedisTemplate redisTemplate;
    private final BotDetectionScorer scorer;
    private final BotDetectionLogRepository botDetectionLogRepository;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void ensureConsumerGroup() {
        try {
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0"), CONSUMER_GROUP);
        } catch (Exception ex) {
            // 그룹이 이미 있거나(BUSYGROUP) Redis가 아직 안 떠 있는 경우 — 폴링 시점에 다시 시도되므로 무시한다.
            log.debug("봇 탐지 컨슈머 그룹 생성 스킵: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${ai.event-poll-interval-ms:5000}")
    public void poll() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                    StreamReadOptions.empty().count(50),
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));
            if (records == null || records.isEmpty()) {
                return;
            }
            for (MapRecord<String, Object, Object> record : records) {
                try {
                    processRecord(record);
                } catch (Exception ex) {
                    // 개별 레코드 처리 실패(예: 잘못된 필드, 존재하지 않는 user/course로 인한 FK 위반)가
                    // 같은 레코드를 영원히 재전달받는 poison message를 만들지 않도록, 실패해도 반드시 ack한다.
                    // 봇 탐지는 부가 기능이라 일부 로그 유실보다 파이프라인이 멈추는 쪽이 더 나쁘다.
                    log.warn("봇 탐지 이벤트 처리 실패(건너뜀): recordId={}, cause={}", record.getId(), ex.getMessage());
                } finally {
                    redisTemplate.opsForStream().acknowledge(STREAM_KEY, CONSUMER_GROUP, record.getId());
                }
            }
        } catch (Exception ex) {
            log.warn("봇 탐지 이벤트 폴링 실패(다음 주기에 재시도): {}", ex.getMessage());
        }
    }

    void processRecord(MapRecord<String, Object, Object> record) {
        Map<Object, Object> value = record.getValue();
        Long userId = Long.valueOf(String.valueOf(value.get("userId")));
        Long courseId = Long.valueOf(String.valueOf(value.get("courseId")));
        String outcome = String.valueOf(value.get("outcome"));
        String intervalRaw = String.valueOf(value.getOrDefault("intervalSeconds", ""));
        Double intervalSeconds = intervalRaw.isBlank() ? null : Double.valueOf(intervalRaw);
        int repeatedCount = Integer.parseInt(String.valueOf(value.get("repeatedCount1Min")));
        boolean userAgentSuspicious = Boolean.parseBoolean(String.valueOf(value.get("userAgentSuspicious")));

        BotDetectionScorer.BotRequestFeatures features = new BotDetectionScorer.BotRequestFeatures(
                userId, courseId, intervalSeconds, repeatedCount, userAgentSuspicious, outcome);
        BotDetectionScorer.ScoringResult result = scorer.score(features);

        botDetectionLogRepository.save(BotDetectionLog.builder()
                .userId(userId)
                .courseId(courseId)
                .requestFeatures(toJson(outcome, intervalSeconds, repeatedCount, userAgentSuspicious))
                .suspicionScore(result.suspicionScore())
                .actionTaken(result.actionTaken())
                .build());
    }

    private String toJson(String outcome, Double intervalSeconds, int repeatedCount, boolean userAgentSuspicious) {
        try {
            Map<String, Object> features = new LinkedHashMap<>();
            features.put("outcome", outcome);
            features.put("intervalSeconds", intervalSeconds);
            features.put("repeatedCount1Min", repeatedCount);
            features.put("userAgentSuspicious", userAgentSuspicious);
            return objectMapper.writeValueAsString(features);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
