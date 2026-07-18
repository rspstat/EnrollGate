package com.enrollgate.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.enrollgate.course.domain.Course;
import com.enrollgate.course.repository.CourseRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 프로젝트의 핵심 주장(정원 초과 판매 0건)을 검증하는 동시성 테스트.
 * 정원보다 많은 동시 요청을 courseRepository.findByIdForUpdate의 비관적 락으로 직렬화해
 * 정확히 capacity 건만 ENROLLED, 나머지는 QUEUED로 처리되는지 확인한다.
 */
@SpringBootTest
class EnrollmentConcurrencyTest {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private CourseRepository courseRepository;

    @Test
    void concurrentEnrollRequestsNeverOversellCapacity() throws Exception {
        int capacity = 3;
        int requesters = 15;

        Course course = Course.builder()
                .courseCode("CSE401")
                .name("데이터베이스시스템")
                .professorName("김OO")
                .department("CSE")
                .credit(3)
                .capacity(capacity)
                .semester("2026-2")
                .enrollmentStartAt(LocalDateTime.now().minusMinutes(1))
                .enrollmentEndAt(LocalDateTime.now().plusHours(1))
                .build();
        Long courseId = courseRepository.save(course).getId();

        ExecutorService executor = Executors.newFixedThreadPool(requesters);
        CountDownLatch ready = new CountDownLatch(requesters);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<EnrollmentOutcome>> futures = new ArrayList<>();
        for (long i = 1; i <= requesters; i++) {
            long userId = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return enrollmentService.enroll(userId, courseId);
            }));
        }

        ready.await();
        start.countDown();

        AtomicLong enrolledCount = new AtomicLong();
        AtomicLong queuedCount = new AtomicLong();
        for (Future<EnrollmentOutcome> future : futures) {
            EnrollmentOutcome outcome = future.get(30, TimeUnit.SECONDS);
            if (outcome instanceof EnrollmentOutcome.Enrolled) {
                enrolledCount.incrementAndGet();
            } else {
                queuedCount.incrementAndGet();
            }
        }
        executor.shutdown();

        assertThat(enrolledCount.get()).isEqualTo(capacity);
        assertThat(queuedCount.get()).isEqualTo(requesters - capacity);

        Course reloaded = courseRepository.findById(courseId).orElseThrow();
        assertThat(reloaded.getCurrentEnrolledCount()).isEqualTo(capacity);
    }
}
