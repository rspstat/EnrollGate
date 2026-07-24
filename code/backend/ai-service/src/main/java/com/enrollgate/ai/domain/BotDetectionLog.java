package com.enrollgate.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * bot_detection_logs 테이블 매핑. request_features는 Postgres에서 JSONB 컬럼이며,
 * Hibernate 6의 {@code @JdbcTypeCode(SqlTypes.JSON)}으로 순수 JSON 문자열 필드에 매핑한다.
 */
@Entity
@Table(name = "bot_detection_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BotDetectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id")
    private Long courseId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_features", nullable = false)
    private String requestFeatures;

    @Column(name = "suspicion_score", nullable = false)
    private Double suspicionScore;

    @Column(name = "action_taken", nullable = false)
    private String actionTaken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public BotDetectionLog(Long userId, Long courseId, String requestFeatures, Double suspicionScore, String actionTaken) {
        this.userId = userId;
        this.courseId = courseId;
        this.requestFeatures = requestFeatures;
        this.suspicionScore = suspicionScore;
        this.actionTaken = actionTaken;
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
