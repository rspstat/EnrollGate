package com.enrollgate.course.service;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis {@code EVAL}(Lua script)로 "잔여 정원 확인 + 증가"를 원자적으로 처리한다.
 * 이 클래스가 이 프로젝트에서 Redis에 직접 접근하는 유일한 지점이다 — redis-atomic 전략이
 * 비활성화된 기본 설정(pessimistic-lock)에서는 이 빈 자체가 생성되지 않으므로,
 * 나머지 코드(테스트 포함)는 Redis가 없어도 전혀 영향을 받지 않는다.
 */
@Component
@ConditionalOnProperty(name = "enrollment.concurrency-strategy", havingValue = "redis-atomic")
public class RedisSeatGate {

    private static final String KEY_PREFIX = "enrollgate:course:";
    private static final String KEY_SUFFIX = ":enrolled-count";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> reserveScript;

    public RedisSeatGate(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.reserveScript = RedisScript.of(new ClassPathResource("scripts/reserve_seat.lua"), Long.class);
    }

    /**
     * 정원 한 자리를 원자적으로 예약한다. 카운터가 아직 없으면(신규 과목) currentDbCount로 시딩한 뒤 한 번 재시도한다.
     */
    public boolean tryReserve(Long courseId, int capacity, int currentDbCount) {
        Long result = execute(courseId, capacity);
        if (result != null && result == -1L) {
            redisTemplate.opsForValue().setIfAbsent(countKey(courseId), String.valueOf(currentDbCount));
            result = execute(courseId, capacity);
        }
        return result != null && result == 1L;
    }

    /**
     * 예약을 되돌린다 (DB 유니크 제약 위반 등으로 신청 확정에 실패했을 때의 보상 트랜잭션).
     */
    public void release(Long courseId) {
        redisTemplate.opsForValue().decrement(countKey(courseId));
    }

    private Long execute(Long courseId, int capacity) {
        return redisTemplate.execute(reserveScript, List.of(countKey(courseId)), String.valueOf(capacity));
    }

    private static String countKey(Long courseId) {
        return KEY_PREFIX + courseId + KEY_SUFFIX;
    }
}
