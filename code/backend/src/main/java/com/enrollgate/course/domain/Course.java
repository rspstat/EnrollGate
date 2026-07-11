package com.enrollgate.course.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_code", nullable = false)
    private String courseCode;

    @Column(nullable = false)
    private String name;

    @Column(name = "professor_name", nullable = false)
    private String professorName;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private Integer credit;

    @Column(nullable = false)
    private Integer capacity;

    @Column(name = "current_enrolled_count", nullable = false)
    private Integer currentEnrolledCount;

    @Column(nullable = false)
    private String semester;

    @Column(name = "enrollment_start_at", nullable = false)
    private LocalDateTime enrollmentStartAt;

    @Column(name = "enrollment_end_at", nullable = false)
    private LocalDateTime enrollmentEndAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public Course(String courseCode, String name, String professorName, String department, Integer credit,
                  Integer capacity, String semester, LocalDateTime enrollmentStartAt, LocalDateTime enrollmentEndAt) {
        this.courseCode = courseCode;
        this.name = name;
        this.professorName = professorName;
        this.department = department;
        this.credit = credit;
        this.capacity = capacity;
        this.currentEnrolledCount = 0;
        this.semester = semester;
        this.enrollmentStartAt = enrollmentStartAt;
        this.enrollmentEndAt = enrollmentEndAt;
    }

    public int remainingSeats() {
        return capacity - currentEnrolledCount;
    }

    /**
     * 정원 초과 여부를 확인 후 증가. 반드시 비관적 락으로 조회한 인스턴스에서 호출해야
     * 동시 요청 상황에서 정합성이 보장된다 (CourseRepository#findByIdForUpdate).
     */
    public void increaseEnrolledCount() {
        if (currentEnrolledCount >= capacity) {
            throw new IllegalStateException("정원이 초과되어 신청할 수 없습니다: courseId=" + id);
        }
        this.currentEnrolledCount++;
    }

    public void decreaseEnrolledCount() {
        if (currentEnrolledCount <= 0) {
            throw new IllegalStateException("잔여 신청 인원이 0인 상태에서 취소를 시도했습니다: courseId=" + id);
        }
        this.currentEnrolledCount--;
    }

    @PrePersist
    private void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
