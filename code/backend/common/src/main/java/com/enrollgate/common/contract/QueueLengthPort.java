package com.enrollgate.common.contract;

/**
 * Course 도메인이 자기 소유가 아닌 대기열 길이를 조회하기 위한 포트.
 * course-service는 이 인터페이스만 알고, 실제 구현(enrollment-service의 WaitingQueueRepository 기반)에는
 * 의존하지 않는다 — 3단계(MSA 분리)에서 서비스 간 순환 의존을 만들지 않기 위한 경계.
 */
public interface QueueLengthPort {

    long queueLength(Long courseId);
}
