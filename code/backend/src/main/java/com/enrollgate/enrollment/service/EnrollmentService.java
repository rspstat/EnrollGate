package com.enrollgate.enrollment.service;

import com.enrollgate.course.domain.Course;
import com.enrollgate.course.repository.CourseRepository;
import com.enrollgate.enrollment.domain.Enrollment;
import com.enrollgate.enrollment.domain.EnrollmentStatus;
import com.enrollgate.enrollment.queue.domain.WaitingQueueEntry;
import com.enrollgate.enrollment.queue.domain.WaitingQueueStatus;
import com.enrollgate.enrollment.queue.service.NoActiveQueueEntryException;
import com.enrollgate.enrollment.queue.service.QueueConfirmExpiredException;
import com.enrollgate.enrollment.queue.service.QueueService;
import com.enrollgate.enrollment.repository.EnrollmentRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청/취소/대기열(enter, confirm, leave, status, 만료 스윕)을 아우르는 오케스트레이터.
 * 정원 카운터(Course)를 건드리는 모든 경로는 courseRepository.findByIdForUpdate로 과목 행을 먼저 잠근 뒤
 * 대기열 상태를 조회/변경한다 — 이 course row 락이 곧 이 서브도메인 전체의 동시성 제어 지점이다.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    /**
     * 대기열 순번당 예상 대기 시간(초) 추정치. 실측 데이터가 없는 상태의 임시 값이며,
     * k6 부하테스트 진행 후(PRD 2단계) 실제 확정 처리 속도 기준으로 조정한다.
     */
    private static final long ESTIMATED_SECONDS_PER_QUEUE_SLOT = 15L;

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QueueService queueService;

    @Value("${queue.confirm-window-seconds:60}")
    private long confirmWindowSeconds;

    @Transactional
    public EnrollmentOutcome enroll(Long userId, Long courseId) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(course.getEnrollmentStartAt()) || now.isAfter(course.getEnrollmentEndAt())) {
            throw new EnrollmentPeriodClosedException(courseId);
        }

        // 재신청 미허용 정책(enrollments 테이블 유니크 제약과 일치): 과거 취소 이력이 있어도 재신청 불가
        if (enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new AlreadyEnrolledException(userId, courseId);
        }

        if (course.remainingSeats() > 0) {
            course.increaseEnrolledCount();
            Enrollment enrollment = enrollmentRepository.save(
                    Enrollment.builder().userId(userId).courseId(courseId).build());
            return new EnrollmentOutcome.Enrolled(enrollment.getId(), enrollment.getEnrolledAt());
        }

        WaitingQueueEntry entry = queueService.enter(userId, courseId);
        long position = queueService.position(entry);
        return new EnrollmentOutcome.Queued(position, position * ESTIMATED_SECONDS_PER_QUEUE_SLOT);
    }

    @Transactional
    public void cancel(Long userId, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        if (!enrollment.getUserId().equals(userId)) {
            // 존재 여부를 노출하지 않기 위해 소유자가 아닐 때도 동일한 404를 반환한다
            throw new EnrollmentNotFoundException(enrollmentId);
        }
        if (enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            throw new EnrollmentAlreadyCancelledException(enrollmentId);
        }

        Course course = courseRepository.findByIdForUpdate(enrollment.getCourseId())
                .orElseThrow(() -> new CourseNotFoundException(enrollment.getCourseId()));

        enrollment.cancel();
        releaseSeatOrPromote(course);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentSummary> listMine(Long userId) {
        return enrollmentRepository.findAllByUserIdOrderByEnrolledAtDesc(userId).stream()
                .map(enrollment -> {
                    Course course = courseRepository.findById(enrollment.getCourseId()).orElse(null);
                    String courseCode = course != null ? course.getCourseCode() : null;
                    String courseName = course != null ? course.getName() : null;
                    return EnrollmentSummary.of(enrollment, courseCode, courseName);
                })
                .toList();
    }

    @Transactional
    public EnrollmentOutcome.Enrolled confirmQueue(Long userId, Long courseId) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        WaitingQueueEntry entry = queueService.findNotified(userId, courseId)
                .orElseThrow(() -> new NoActiveQueueEntryException(userId, courseId));

        if (entry.isExpired(LocalDateTime.now())) {
            entry.expire();
            releaseSeatOrPromote(course);
            throw new QueueConfirmExpiredException(courseId);
        }

        entry.confirm();
        Enrollment enrollment = enrollmentRepository.save(
                Enrollment.builder().userId(userId).courseId(courseId).build());
        return new EnrollmentOutcome.Enrolled(enrollment.getId(), enrollment.getEnrolledAt());
    }

    @Transactional
    public void leaveQueue(Long userId, Long courseId) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        WaitingQueueEntry entry = queueService.findActive(userId, courseId)
                .orElseThrow(() -> new NoActiveQueueEntryException(userId, courseId));

        boolean wasNotified = entry.getStatus() == WaitingQueueStatus.NOTIFIED;
        entry.cancel();
        if (wasNotified) {
            releaseSeatOrPromote(course);
        }
    }

    @Transactional(readOnly = true)
    public QueueStatusResult queueStatus(Long userId, Long courseId) {
        WaitingQueueEntry entry = queueService.findActive(userId, courseId)
                .orElseThrow(() -> new NoActiveQueueEntryException(userId, courseId));

        if (entry.isExpired(LocalDateTime.now())) {
            // 실제 만료 처리(EXPIRED 전이 + 다음 순번 승격)는 QueueExpirySweeper가 담당한다.
            throw new NoActiveQueueEntryException(userId, courseId);
        }

        if (entry.getStatus() == WaitingQueueStatus.WAITING) {
            long position = queueService.position(entry);
            return QueueStatusResult.waiting(position, position * ESTIMATED_SECONDS_PER_QUEUE_SLOT);
        }
        return QueueStatusResult.notified(entry.getExpiresAt());
    }

    @Transactional
    public void expireOverdueQueueEntries() {
        LocalDateTime now = LocalDateTime.now();
        for (WaitingQueueEntry entry : queueService.findExpiredNotified(now)) {
            Course course = courseRepository.findByIdForUpdate(entry.getCourseId())
                    .orElseThrow(() -> new CourseNotFoundException(entry.getCourseId()));
            entry.expire();
            releaseSeatOrPromote(course);
        }
    }

    private void releaseSeatOrPromote(Course course) {
        Optional<WaitingQueueEntry> promoted = queueService.promoteNext(course.getId(), Duration.ofSeconds(confirmWindowSeconds));
        if (promoted.isEmpty()) {
            course.decreaseEnrolledCount();
        }
    }
}
