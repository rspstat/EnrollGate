package com.enrollgate.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 실제 로컬 Redis(포터블 바이너리, localhost:6379)에 붙어 Lua EVAL 기반 원자 예약 로직을 검증한다.
 * Redis가 없는 환경(CI 등)에서는 자동으로 건너뛴다 — 이 프로젝트의 기본 테스트 스위트(H2 기반)는
 * Redis 없이도 항상 통과해야 하므로, 이 테스트만 예외적으로 "가능하면 실행, 없으면 스킵" 방식을 쓴다.
 */
class RedisSeatGateIntegrationTest {

    private static final AtomicLong COURSE_ID_SEQUENCE = new AtomicLong(900_000);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisSeatGate redisSeatGate;
    private Long courseId;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory("localhost", 6379);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        boolean reachable;
        try {
            reachable = "PONG".equalsIgnoreCase(
                    redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<String>)
                            connection -> connection.ping()));
        } catch (Exception ex) {
            reachable = false;
        }
        assumeTrue(reachable, "로컬 Redis(localhost:6379)에 연결할 수 없어 이 테스트를 건너뜁니다");

        redisSeatGate = new RedisSeatGate(redisTemplate);
        courseId = COURSE_ID_SEQUENCE.incrementAndGet();
    }

    @AfterEach
    void tearDown() {
        if (redisTemplate != null && courseId != null) {
            redisTemplate.delete("enrollgate:course:" + courseId + ":enrolled-count");
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void tryReserve_lazilySeedsFromDbCount_thenAtomicallyGatesUpToCapacity() {
        // DB 상 이미 2명이 신청한 상태(capacity=3)에서 Redis 카운터가 아직 시딩되지 않은 첫 호출
        boolean first = redisSeatGate.tryReserve(courseId, 3, 2);
        assertThat(first).isTrue(); // 2 -> 3, 성공

        boolean second = redisSeatGate.tryReserve(courseId, 3, 2);
        assertThat(second).isFalse(); // 이미 3/3, 정원 초과
    }

    @Test
    void tryReserve_allowsExactlyCapacityReservations_fromFreshCounter() {
        int capacity = 5;
        int successCount = 0;
        for (int i = 0; i < capacity + 3; i++) {
            if (redisSeatGate.tryReserve(courseId, capacity, 0)) {
                successCount++;
            }
        }
        assertThat(successCount).isEqualTo(capacity);
    }

    @Test
    void release_decrementsCounter_allowingOneMoreReservation() {
        int capacity = 1;
        assertThat(redisSeatGate.tryReserve(courseId, capacity, 0)).isTrue();
        assertThat(redisSeatGate.tryReserve(courseId, capacity, 0)).isFalse();

        redisSeatGate.release(courseId);

        assertThat(redisSeatGate.tryReserve(courseId, capacity, 0)).isTrue();
    }

    @Test
    void tryReserve_isSafeUnderConcurrentAccess() throws InterruptedException {
        int capacity = 10;
        int requesters = 50;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(requesters);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(requesters);

        for (int i = 0; i < requesters; i++) {
            executor.submit(() -> {
                try {
                    if (redisSeatGate.tryReserve(courseId, capacity, 0)) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(capacity);
    }
}
