package com.enrollgate.enrollment.queue.service;

import com.enrollgate.enrollment.queue.domain.WaitingQueueEntry;
import com.enrollgate.enrollment.queue.domain.WaitingQueueStatus;
import com.enrollgate.enrollment.queue.repository.WaitingQueueRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * waiting_queue 테이블에 대한 순수 조회/전이만 담당한다. Course 정원 카운터와의 정합성 조율은
 * EnrollmentService가 course row 비관적 락을 잡은 뒤 이 서비스를 호출하는 방식으로 책임진다
 * (이 서비스 자체는 Course/Enrollment를 참조하지 않는다).
 */
@Service
@RequiredArgsConstructor
public class QueueService {

    private static final List<WaitingQueueStatus> ACTIVE_STATUSES =
            List.of(WaitingQueueStatus.WAITING, WaitingQueueStatus.NOTIFIED);

    private final WaitingQueueRepository waitingQueueRepository;

    public WaitingQueueEntry enter(Long userId, Long courseId) {
        if (waitingQueueRepository.existsByUserIdAndCourseIdAndStatusIn(userId, courseId, ACTIVE_STATUSES)) {
            throw new AlreadyQueuedException(userId, courseId);
        }
        return waitingQueueRepository.save(WaitingQueueEntry.builder().userId(userId).courseId(courseId).build());
    }

    public long position(WaitingQueueEntry entry) {
        return waitingQueueRepository.countByCourseIdAndStatusAndEnteredAtBefore(
                entry.getCourseId(), WaitingQueueStatus.WAITING, entry.getEnteredAt()) + 1;
    }

    public long queueLength(Long courseId) {
        return waitingQueueRepository.countByCourseIdAndStatusIn(courseId, ACTIVE_STATUSES);
    }

    public Optional<WaitingQueueEntry> findActive(Long userId, Long courseId) {
        return waitingQueueRepository.findFirstByUserIdAndCourseIdAndStatusInOrderByEnteredAtDesc(userId, courseId, ACTIVE_STATUSES);
    }

    public Optional<WaitingQueueEntry> findNotified(Long userId, Long courseId) {
        return waitingQueueRepository.findByUserIdAndCourseIdAndStatus(userId, courseId, WaitingQueueStatus.NOTIFIED);
    }

    /**
     * 정원 한 자리가 비었을 때 가장 먼저 대기 중인 사용자를 NOTIFIED로 승격한다.
     * 반드시 호출자가 해당 course row에 대한 비관적 락을 잡은 상태에서 호출해야 한다.
     */
    public Optional<WaitingQueueEntry> promoteNext(Long courseId, Duration confirmWindow) {
        return waitingQueueRepository.findFirstByCourseIdAndStatusOrderByEnteredAtAsc(courseId, WaitingQueueStatus.WAITING)
                .map(entry -> {
                    entry.notifyTurn(confirmWindow);
                    return entry;
                });
    }

    public List<WaitingQueueEntry> findExpiredNotified(LocalDateTime now) {
        return waitingQueueRepository.findByStatusAndExpiresAtBefore(WaitingQueueStatus.NOTIFIED, now);
    }
}
