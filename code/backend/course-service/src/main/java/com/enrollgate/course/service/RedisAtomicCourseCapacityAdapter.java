package com.enrollgate.course.service;

import com.enrollgate.common.contract.CourseCapacityPort;
import com.enrollgate.course.domain.Course;
import com.enrollgate.course.repository.CourseRepository;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2단계 성능 개선안: 과목 행을 잠그지 않고 Redis EVAL(Lua script)로 "잔여 정원 확인 + 증가"를 원자적으로
 * 처리한 뒤 DB에는 단일 원자 UPDATE(선행 SELECT 없음)로 반영한다.
 *
 * <p>취소/만료 경로({@link #releaseSeatOrElse})는 이 전략에서도 항상 비관적 락을 사용한다 — Redis 카운터는
 * 감소분을 반영하지 않으므로, 이 전략이 활성화된 동안 취소가 발생하면 Redis 카운터가 실제보다 커진 채로
 * 남을 수 있다(정원 초과 판매 방향은 아니고, 다음 신청자를 과도하게 대기열로 보내는 방향의 오차). k6 A/B
 * 성능 비교(신청 자체의 처리량)가 목적이므로 이 범위에서는 허용 가능한 트레이드오프로 문서화한다
 * (README "동시성 제어 전략" 절 참고).
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enrollment.concurrency-strategy", havingValue = "redis-atomic")
public class RedisAtomicCourseCapacityAdapter implements CourseCapacityPort {

    private final CourseRepository courseRepository;
    private final RedisSeatGate redisSeatGate;

    @Override
    @Transactional(readOnly = true)
    public CourseSnapshot getSnapshot(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        return toSnapshot(course);
    }

    @Override
    @Transactional
    public ReservationAttempt attemptReservation(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        CourseSnapshot snapshot = toSnapshot(course);
        boolean reserved = redisSeatGate.tryReserve(courseId, course.getCapacity(), course.getCurrentEnrolledCount());
        if (reserved) {
            courseRepository.incrementEnrolledCount(courseId);
        }
        return new ReservationAttempt(snapshot, reserved);
    }

    @Override
    @Transactional
    public void compensateReserve(Long courseId) {
        // Redis 카운터와 DB 카운터 둘 다 reserveSeat()에서 증가시켰으므로 둘 다 되돌린다.
        redisSeatGate.release(courseId);
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        course.decreaseEnrolledCount();
    }

    @Override
    @Transactional
    public void releaseSeatOrElse(Long courseId, Supplier<Boolean> promotionAttempt) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        boolean promoted = promotionAttempt.get();
        if (!promoted) {
            course.decreaseEnrolledCount();
        }
    }

    private CourseSnapshot toSnapshot(Course course) {
        return new CourseSnapshot(course.getId(), course.getCourseCode(), course.getName(), course.getCapacity(), course.getCurrentEnrolledCount(),
                course.getEnrollmentStartAt(), course.getEnrollmentEndAt());
    }
}
