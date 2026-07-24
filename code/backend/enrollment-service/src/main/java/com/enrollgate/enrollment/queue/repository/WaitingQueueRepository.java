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

    /**
     * 대기 순번 계산용. entered_at(타임스탬프) 대신 id(IDENTITY, 삽입 순서와 항상 일치)로 비교한다 —
     * 타임스탬프는 DB 컬럼 저장 정밀도가 Java LocalDateTime과 정확히 일치하지 않을 수 있어(H2에서 실측됨),
     * 방금 삽입한 행 자신을 "자기보다 이전"으로 잘못 세는 경계 버그가 생길 수 있다.
     */
    long countByCourseIdAndStatusAndIdLessThan(Long courseId, WaitingQueueStatus status, Long id);

    long countByCourseIdAndStatusIn(Long courseId, Collection<WaitingQueueStatus> statuses);

    Optional<WaitingQueueEntry> findFirstByCourseIdAndStatusOrderByEnteredAtAsc(Long courseId, WaitingQueueStatus status);

    List<WaitingQueueEntry> findByCourseIdAndStatusOrderByEnteredAtAsc(Long courseId, WaitingQueueStatus status);

    List<WaitingQueueEntry> findByStatusAndExpiresAtBefore(WaitingQueueStatus status, LocalDateTime time);
}
