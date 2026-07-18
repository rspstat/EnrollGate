package com.enrollgate.enrollment.queue.repository;

import com.enrollgate.enrollment.queue.domain.WaitingQueueEntry;
import com.enrollgate.enrollment.queue.domain.WaitingQueueStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitingQueueRepository extends JpaRepository<WaitingQueueEntry, Long> {

    boolean existsByUserIdAndCourseIdAndStatusIn(Long userId, Long courseId, Collection<WaitingQueueStatus> statuses);

    Optional<WaitingQueueEntry> findFirstByUserIdAndCourseIdAndStatusInOrderByEnteredAtDesc(
            Long userId, Long courseId, Collection<WaitingQueueStatus> statuses);

    Optional<WaitingQueueEntry> findByUserIdAndCourseIdAndStatus(Long userId, Long courseId, WaitingQueueStatus status);

    long countByCourseIdAndStatusAndEnteredAtBefore(Long courseId, WaitingQueueStatus status, LocalDateTime enteredAt);

    long countByCourseIdAndStatusIn(Long courseId, Collection<WaitingQueueStatus> statuses);

    Optional<WaitingQueueEntry> findFirstByCourseIdAndStatusOrderByEnteredAtAsc(Long courseId, WaitingQueueStatus status);

    List<WaitingQueueEntry> findByStatusAndExpiresAtBefore(WaitingQueueStatus status, LocalDateTime time);
}
