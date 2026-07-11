package com.enrollgate.enrollment.service;

import com.enrollgate.course.domain.Course;
import com.enrollgate.course.repository.CourseRepository;
import com.enrollgate.enrollment.domain.Enrollment;
import com.enrollgate.enrollment.domain.EnrollmentStatus;
import com.enrollgate.enrollment.repository.EnrollmentRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    /**
     * 동시성 제어 지점: findByIdForUpdate가 SELECT ... FOR UPDATE로 과목 행을 잠그기 때문에,
     * 같은 courseId로 들어오는 동시 요청은 이 트랜잭션이 끝날 때까지 대기한다.
     * 따라서 currentEnrolledCount 증가와 정원 초과 검사가 원자적으로 처리된다.
     */
    @Transactional
    public EnrollmentResult enroll(Long userId, Long courseId) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(course.getEnrollmentStartAt()) || now.isAfter(course.getEnrollmentEndAt())) {
            throw new EnrollmentPeriodClosedException(courseId);
        }

        if (enrollmentRepository.existsByUserIdAndCourseIdAndStatus(userId, courseId, EnrollmentStatus.ENROLLED)) {
            throw new AlreadyEnrolledException(userId, courseId);
        }

        if (course.remainingSeats() <= 0) {
            throw new CourseFullException(courseId);
        }

        course.increaseEnrolledCount();

        Enrollment enrollment = enrollmentRepository.save(
                Enrollment.builder()
                        .userId(userId)
                        .courseId(courseId)
                        .build());

        return new EnrollmentResult(enrollment.getId(), enrollment.getEnrolledAt());
    }
}
