package com.enrollgate.enrollment.queue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 감사 로그 겸 1단계(DB 폴링 수준) 대기열 저장소. 실시간 순번 관리를 Redis Sorted Set으로 옮기는 것은
 * 2단계 과제이며, 그 전까지는 이 테이블의 entered_at 순서가 곧 대기열 순서다.
 */
@Entity
@Table(name = "waiting_queue")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitingQueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WaitingQueueStatus status;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Builder
    public WaitingQueueEntry(Long userId, Long courseId) {
        this.userId = userId;
        this.courseId = courseId;
        this.status = WaitingQueueStatus.WAITING;
    }

    @PrePersist
    private void onCreate() {
        this.enteredAt = LocalDateTime.now();
    }

    /**
     * 정원 한 자리가 비어 이 항목이 대기열 맨 앞으로 승격될 때 호출한다.
     * 호출자가 해당 course row에 비관적 락을 잡은 상태여야 currentEnrolledCount와의 정합성이 보장된다.
     */
    public void notifyTurn(Duration confirmWindow) {
        if (this.status != WaitingQueueStatus.WAITING) {
            throw new IllegalStateException("대기 중 상태에서만 순번 알림으로 전이할 수 있습니다: id=" + id);
        }
        this.status = WaitingQueueStatus.NOTIFIED;
        this.notifiedAt = LocalDateTime.now();
        this.expiresAt = this.notifiedAt.plus(confirmWindow);
    }

    public void confirm() {
        if (this.status != WaitingQueueStatus.NOTIFIED) {
            throw new IllegalStateException("순번 알림 상태에서만 확정할 수 있습니다: id=" + id);
        }
        this.status = WaitingQueueStatus.CONFIRMED;
    }

    public void expire() {
        this.status = WaitingQueueStatus.EXPIRED;
    }

    public void cancel() {
        this.status = WaitingQueueStatus.CANCELLED;
    }

    public boolean isExpired(LocalDateTime now) {
        return status == WaitingQueueStatus.NOTIFIED && expiresAt != null && now.isAfter(expiresAt);
    }
}
