package com.enrollgate.common.contract;

import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Enrollment 도메인이 Course의 정원 데이터/카운터를 조작하기 위한 포트. enrollment-service는 이 인터페이스와
 * {@link CourseSnapshot}만 알고, Course 엔티티나 리포지토리에는 전혀 의존하지 않는다 — 3단계(MSA 분리)에서
 * 서비스 경계를 강제하기 위한 지점. 실제 구현(비관적 락/Redis 원자 연산 두 전략)은 course-service가 제공한다.
 *
 * <p>{@link #releaseSeatOrElse}가 콜백을 받는 이유: 좌석 반납 여부를 정할 때 "다음 대기자에게 순번을 넘길지"를
 * 먼저 확인해야 하는데, 그 대기열 승격 로직은 enrollment 도메인 소유다. course-service는 course row에 대한
 * (전략과 무관하게 항상 비관적 락 기반) 배타적 임계구역만 보장하고, 그 안에서 enrollment가 넘겨준 콜백을 실행해
 * "승격 성공 여부"를 돌려받아 카운터 증감을 결정한다. 이렇게 하면 두 모듈이 서로의 내부 타입을 몰라도
 * 기존의 락 직렬화 보장을 그대로 유지할 수 있다.
 */
public interface CourseCapacityPort {

    /** 조회 전용(display/listMine 용). 잠그지 않는다. */
    CourseSnapshot getSnapshot(Long courseId);

    /**
     * 스냅샷 확인과 정원 예약을 한 번의 잠금 아래 원자적으로 처리한다(전략에 따라 비관적 락 또는 Redis 원자 연산).
     * enroll() 흐름 전용 — {@link #getSnapshot}과 별개로 둔 이유: 같은 트랜잭션 안에서 Course를 먼저
     * (잠금 없이) 읽고 곧이어 잠금으로 다시 읽으면, 이미 영속성 컨텍스트에 올라온 엔티티 때문에 두 번째 조회가
     * 실제 DB 락으로 이어지지 않는 경우가 있다(Hibernate 1차 캐시). 그래서 enroll()은 Course를 이 메서드
     * 하나로만 읽고 잠근다.
     */
    ReservationAttempt attemptReservation(Long courseId);

    /**
     * reserveSeat()가 성공한 뒤, 뒤이은 Enrollment 저장이 유니크 제약 위반 등으로 실패했을 때 예약을 되돌린다
     * (동시 중복 신청 경합에 대한 보상 트랜잭션). 전략별로 무엇을 되돌려야 하는지 다르므로(Redis는 Redis 카운터도
     * 되돌려야 함) 이 메서드도 전략에 따라 구현이 갈린다.
     */
    void compensateReserve(Long courseId);

    /**
     * course row를 (전략 무관하게 항상 비관적 락으로) 잠근 채로 promotionAttempt를 실행한다.
     * true를 반환하면(대기자에게 승격) 카운터를 그대로 두고, false면 카운터를 감소시킨다.
     */
    void releaseSeatOrElse(Long courseId, Supplier<Boolean> promotionAttempt);

    record CourseSnapshot(
            Long id,
            String courseCode,
            String name,
            Integer capacity,
            Integer currentEnrolledCount,
            LocalDateTime enrollmentStartAt,
            LocalDateTime enrollmentEndAt
    ) {
        public int remainingSeats() {
            return capacity - currentEnrolledCount;
        }
    }

    record ReservationAttempt(CourseSnapshot snapshot, boolean reserved) {
    }
}
