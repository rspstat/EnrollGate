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
 * 1단계 베이스라인: JPA {@code @Lock(PESSIMISTIC_WRITE)} → SELECT ... FOR UPDATE로 과목 행을 잠근 뒤
 * 정원 확인과 카운터 증가를 원자적으로 처리한다.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enrollment.concurrency-strategy", havingValue = "pessimistic-lock", matchIfMissing = true)
public class PessimisticLockCourseCapacityAdapter implements CourseCapacityPort {

    private final CourseRepository courseRepository;

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
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        CourseSnapshot snapshot = toSnapshot(course);
        if (course.remainingSeats() <= 0) {
            return new ReservationAttempt(snapshot, false);
        }
        course.increaseEnrolledCount();
        return new ReservationAttempt(snapshot, true);
    }

    @Override
    @Transactional
    public void compensateReserve(Long courseId) {
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
