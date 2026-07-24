package com.enrollgate.enrollment.service;

import com.enrollgate.common.contract.CourseCapacityPort;
import com.enrollgate.common.contract.CourseCapacityPort.CourseSnapshot;
import com.enrollgate.enrollment.domain.Enrollment;
import com.enrollgate.enrollment.domain.EnrollmentStatus;
import com.enrollgate.enrollment.queue.domain.WaitingQueueEntry;
import com.enrollgate.enrollment.queue.domain.WaitingQueueStatus;
import com.enrollgate.enrollment.queue.service.NoActiveQueueEntryException;
import com.enrollgate.enrollment.queue.service.QueueConfirmExpiredException;
import com.enrollgate.enrollment.queue.service.QueueService;
import com.enrollgate.enrollment.queue.websocket.QueueNotificationService;
import com.enrollgate.enrollment.repository.EnrollmentRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신청/취소/대기열(confirm, leave, status, 만료 스윕)을 아우르는 오케스트레이터. 정원 카운터를 건드리는 모든 경로는
 * {@link CourseCapacityPort}를 통해서만 이뤄진다 — 이 서비스는 Course 엔티티/리포지토리를 전혀 알지 못한다
 * (3단계 MSA 분리 경계). 실제 락 방식(비관적 락 vs Redis 원자 연산)은 course-service의 포트 구현이 결정한다.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final QueueService queueService;
    private final CourseCapacityPort courseCapacityPort;
    private final QueueNotificationService queueNotificationService;

    @Value("${queue.confirm-window-seconds:60}")
    private long confirmWindowSeconds;

    @Transactional
    public EnrollmentOutcome enroll(Long userId, Long courseId) {
        // 재신청 미허용 정책(enrollments 테이블 유니크 제약과 일치): 과거 취소 이력이 있어도 재신청 불가.
        // Course 조회가 필요 없는 확인이라 락 획득 전에 먼저 걸러낸다.
        if (enrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new AlreadyEnrolledException(userId, courseId);
        }

        // 정원 확인과 예약을 한 번의 잠금 아래 원자적으로 처리한다. getSnapshot()을 따로 먼저 호출하지 않는 이유는
        // CourseCapacityPort 문서 참고 — 같은 트랜잭션에서 잠금 없이 읽은 뒤 잠금으로 다시 읽으면 Hibernate
        // 1차 캐시 때문에 실제 DB 락으로 이어지지 않을 수 있다.
        CourseCapacityPort.ReservationAttempt attempt = courseCapacityPort.attemptReservation(courseId);
        CourseSnapshot snapshot = attempt.snapshot();

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(snapshot.enrollmentStartAt()) || now.isAfter(snapshot.enrollmentEndAt())) {
            if (attempt.reserved()) {
                courseCapacityPort.compensateReserve(courseId);
            }
            throw new EnrollmentPeriodClosedException(courseId);
        }

        if (!attempt.reserved()) {
            return queueService.enterQueue(userId, courseId);
        }

        try {
            Enrollment enrollment = enrollmentRepository.save(
                    Enrollment.builder().userId(userId).courseId(courseId).build());
            return new EnrollmentOutcome.Enrolled(enrollment.getId(), enrollment.getEnrolledAt());
        } catch (DataIntegrityViolationException ex) {
            // 동시 중복 신청 경합으로 유니크 제약에 걸린 드문 경우: 예약을 보상 트랜잭션으로 되돌린다.
            courseCapacityPort.compensateReserve(courseId);
            throw new AlreadyEnrolledException(userId, courseId);
        }
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

        enrollment.cancel();
        releaseSeatOrPromote(enrollment.getCourseId());
    }

    @Transactional(readOnly = true)
    public List<EnrollmentSummary> listMine(Long userId) {
        return enrollmentRepository.findAllByUserIdOrderByEnrolledAtDesc(userId).stream()
                .map(enrollment -> {
                    CourseSnapshot snapshot = safeSnapshot(enrollment.getCourseId());
                    String courseCode = snapshot != null ? snapshot.courseCode() : null;
                    String courseName = snapshot != null ? snapshot.name() : null;
                    return EnrollmentSummary.of(enrollment, courseCode, courseName);
                })
                .toList();
    }

    @Transactional
    public EnrollmentOutcome.Enrolled confirmQueue(Long userId, Long courseId) {
        WaitingQueueEntry entry = queueService.findNotified(userId, courseId)
                .orElseThrow(() -> new NoActiveQueueEntryException(userId, courseId));

        if (entry.isExpired(LocalDateTime.now())) {
            entry.expire();
            queueNotificationService.notifyExpired(courseId, userId);
            releaseSeatOrPromote(courseId);
            throw new QueueConfirmExpiredException(courseId);
        }

        entry.confirm();
        Enrollment enrollment = enrollmentRepository.save(
                Enrollment.builder().userId(userId).courseId(courseId).build());
        return new EnrollmentOutcome.Enrolled(enrollment.getId(), enrollment.getEnrolledAt());
    }

    @Transactional
    public void leaveQueue(Long userId, Long courseId) {
        WaitingQueueEntry entry = queueService.findActive(userId, courseId)
                .orElseThrow(() -> new NoActiveQueueEntryException(userId, courseId));

        boolean wasNotified = entry.getStatus() == WaitingQueueStatus.NOTIFIED;
        entry.cancel();
        if (wasNotified) {
            releaseSeatOrPromote(courseId);
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
            return QueueStatusResult.waiting(position, position * QueueService.ESTIMATED_SECONDS_PER_QUEUE_SLOT);
        }
        return QueueStatusResult.notified(entry.getExpiresAt());
    }

    @Transactional
    public void expireOverdueQueueEntries() {
        LocalDateTime now = LocalDateTime.now();
        for (WaitingQueueEntry entry : queueService.findExpiredNotified(now)) {
            entry.expire();
            queueNotificationService.notifyExpired(entry.getCourseId(), entry.getUserId());
            releaseSeatOrPromote(entry.getCourseId());
        }
    }

    private void releaseSeatOrPromote(Long courseId) {
        courseCapacityPort.releaseSeatOrElse(courseId, () -> {
            Optional<WaitingQueueEntry> promoted = queueService.promoteNext(courseId, Duration.ofSeconds(confirmWindowSeconds));
            promoted.ifPresent(entry ->
                    queueNotificationService.notifyYourTurn(courseId, entry.getUserId(), entry.getExpiresAt()));
            return promoted.isPresent();
        });
        broadcastPositionUpdates(courseId);
    }

    /** 승격/이탈로 대기 순번이 한 칸씩 당겨진 나머지 대기자들에게 갱신된 순번을 push한다. */
    private void broadcastPositionUpdates(Long courseId) {
        for (WaitingQueueEntry entry : queueService.findWaitingEntriesInOrder(courseId)) {
            long position = queueService.position(entry);
            queueNotificationService.notifyPositionUpdate(
                    courseId, entry.getUserId(), position, position * QueueService.ESTIMATED_SECONDS_PER_QUEUE_SLOT);
        }
    }

    private CourseSnapshot safeSnapshot(Long courseId) {
        try {
            return courseCapacityPort.getSnapshot(courseId);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
