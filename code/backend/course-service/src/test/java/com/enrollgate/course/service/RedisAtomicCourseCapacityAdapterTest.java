package com.enrollgate.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enrollgate.course.domain.Course;
import com.enrollgate.course.repository.CourseRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RedisAtomicCourseCapacityAdapterTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private RedisSeatGate redisSeatGate;

    @InjectMocks
    private RedisAtomicCourseCapacityAdapter adapter;

    private Course course(int capacity, int currentEnrolledCount) {
        Course course = Course.builder()
                .courseCode("CSE401").name("데이터베이스시스템").professorName("김OO").department("CSE")
                .credit(3).capacity(capacity).semester("2026-2")
                .enrollmentStartAt(LocalDateTime.now().minusDays(1))
                .enrollmentEndAt(LocalDateTime.now().plusDays(1))
                .build();
        ReflectionTestUtils.setField(course, "id", 100L);
        for (int i = 0; i < currentEnrolledCount; i++) {
            course.increaseEnrolledCount();
        }
        return course;
    }

    @Test
    void attemptReservation_incrementsDb_whenRedisGrants() {
        when(courseRepository.findById(100L)).thenReturn(Optional.of(course(3, 0)));
        when(redisSeatGate.tryReserve(100L, 3, 0)).thenReturn(true);

        var attempt = adapter.attemptReservation(100L);

        assertThat(attempt.reserved()).isTrue();
        verify(courseRepository).incrementEnrolledCount(100L);
    }

    @Test
    void attemptReservation_doesNotTouchDb_whenRedisDenies() {
        when(courseRepository.findById(100L)).thenReturn(Optional.of(course(1, 1)));
        when(redisSeatGate.tryReserve(eq(100L), anyInt(), anyInt())).thenReturn(false);

        var attempt = adapter.attemptReservation(100L);

        assertThat(attempt.reserved()).isFalse();
        verify(courseRepository, never()).incrementEnrolledCount(100L);
    }

    @Test
    void compensateReserve_releasesRedisAndDecrementsDb() {
        Course course = course(3, 1);
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));

        adapter.compensateReserve(100L);

        verify(redisSeatGate).release(100L);
        assertThat(course.getCurrentEnrolledCount()).isEqualTo(0);
    }

    @Test
    void releaseSeatOrElse_alwaysUsesPessimisticLock_regardlessOfRedis() {
        Course course = course(3, 1);
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));

        adapter.releaseSeatOrElse(100L, () -> false);

        assertThat(course.getCurrentEnrolledCount()).isEqualTo(0);
        verify(redisSeatGate, never()).release(100L);
    }
}
