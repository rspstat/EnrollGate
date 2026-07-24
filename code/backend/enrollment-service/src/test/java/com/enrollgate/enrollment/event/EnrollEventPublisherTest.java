package com.enrollgate.enrollment.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 실제 로컬 Redis에 붙어 이벤트 발행 + 피처 계산(간격/반복횟수)을 검증한다. Redis가 없으면 자동 스킵한다
 * (이 프로젝트 기본 테스트 스위트는 Redis 없이도 항상 통과해야 함 — RedisSeatGateIntegrationTest와 동일한 방침).
 */
class EnrollEventPublisherTest {

    private static final String STREAM_KEY = "enrollgate:enroll-events";
    private static final AtomicLong USER_ID_SEQUENCE = new AtomicLong(800_000);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private EnrollEventPublisher publisher;
    private Long userId;
    private Long courseId;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        boolean reachable;
        try {
            reachable = "PONG".equalsIgnoreCase(redisTemplate.execute(
                    (org.springframework.data.redis.core.RedisCallback<String>) connection -> connection.ping()));
        } catch (Exception ex) {
            reachable = false;
        }
        assumeTrue(reachable, "로컬 Redis(localhost:6379)에 연결할 수 없어 이 테스트를 건너뜁니다");

        publisher = new EnrollEventPublisher(redisTemplate);
        userId = USER_ID_SEQUENCE.incrementAndGet();
        courseId = 900_000L;
    }

    @AfterEach
    void tearDown() {
        if (redisTemplate != null) {
            redisTemplate.delete("enrollgate:bot-detect:last-request:" + userId);
            redisTemplate.delete("enrollgate:bot-detect:req-count:" + userId + ":" + courseId);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    private MapRecord<String, Object, Object> latestRecordFor(Long userId) {
        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .read(StreamOffset.create(STREAM_KEY, ReadOffset.from("0")));
        return records.stream()
                .filter(r -> String.valueOf(userId).equals(String.valueOf(r.getValue().get("userId"))))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("발행된 이벤트를 찾지 못함: userId=" + userId));
    }

    @Test
    void publish_recordsFirstRequestWithNoInterval() {
        publisher.publish(userId, courseId, "Mozilla/5.0", "ENROLLED");

        MapRecord<String, Object, Object> record = latestRecordFor(userId);
        Map<Object, Object> value = record.getValue();
        assertThat(value.get("intervalSeconds")).isEqualTo("");
        assertThat(value.get("repeatedCount1Min")).isEqualTo("1");
        assertThat(value.get("userAgentSuspicious")).isEqualTo("false");
        assertThat(value.get("outcome")).isEqualTo("ENROLLED");
    }

    @Test
    void publish_computesIntervalAndIncrementsRepeatedCount_onSecondCall() throws InterruptedException {
        publisher.publish(userId, courseId, "Mozilla/5.0", "QUEUED");
        Thread.sleep(50);
        publisher.publish(userId, courseId, "Mozilla/5.0", "ALREADY_ENROLLED");

        MapRecord<String, Object, Object> record = latestRecordFor(userId);
        Map<Object, Object> value = record.getValue();
        double interval = Double.parseDouble(String.valueOf(value.get("intervalSeconds")));
        assertThat(interval).isGreaterThan(0.0).isLessThan(2.0);
        assertThat(value.get("repeatedCount1Min")).isEqualTo("2");
    }

    @Test
    void publish_flagsSuspiciousUserAgent() {
        publisher.publish(userId, courseId, "python-requests/2.31", "ENROLLED");

        MapRecord<String, Object, Object> record = latestRecordFor(userId);
        assertThat(record.getValue().get("userAgentSuspicious")).isEqualTo("true");
    }

    @Test
    void publish_neverThrows_evenIfRedisMisbehaves() {
        // 공유 connectionFactory(tearDown에서도 씀)가 아닌 별도 인스턴스를 망가뜨려 격리한다.
        LettuceConnectionFactory brokenFactory = new LettuceConnectionFactory("localhost", 1);
        brokenFactory.afterPropertiesSet();
        brokenFactory.destroy();
        StringRedisTemplate brokenTemplate = new StringRedisTemplate(brokenFactory);
        brokenTemplate.afterPropertiesSet();
        EnrollEventPublisher brokenPublisher = new EnrollEventPublisher(brokenTemplate);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> brokenPublisher.publish(userId, courseId, "Mozilla/5.0", "ENROLLED"));
    }
}
