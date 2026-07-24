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

    /**
     * 관리자용 부분 수정. null인 필드는 변경하지 않는다.
     */
    public void updateDetails(String name, String professorName, String department, Integer credit,
                               LocalDateTime enrollmentStartAt, LocalDateTime enrollmentEndAt) {
        if (name != null) {
            this.name = name;
        }
        if (professorName != null) {
            this.professorName = professorName;
        }
        if (department != null) {
            this.department = department;
        }
        if (credit != null) {
            this.credit = credit;
        }
        if (enrollmentStartAt != null) {
            this.enrollmentStartAt = enrollmentStartAt;
        }
        if (enrollmentEndAt != null) {
            this.enrollmentEndAt = enrollmentEndAt;
        }
    }

    public void updateCapacity(int newCapacity) {
        if (newCapacity < currentEnrolledCount) {
            throw new IllegalArgumentException(
                    "정원은 현재 신청 인원보다 작을 수 없습니다: current=" + currentEnrolledCount + ", requested=" + newCapacity);
        }
        this.capacity = newCapacity;
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
