package com.enrollgate.enrollment.repository;

import com.enrollgate.enrollment.domain.Enrollment;
import com.enrollgate.enrollment.domain.EnrollmentStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByUserIdAndCourseIdAndStatus(Long userId, Long courseId, EnrollmentStatus status);

    Optional<Enrollment> findByUserIdAndCourseIdAndStatus(Long userId, Long courseId, EnrollmentStatus status);
}
