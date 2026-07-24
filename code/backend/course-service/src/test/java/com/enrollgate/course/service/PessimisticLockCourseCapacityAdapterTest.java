package com.enrollgate.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.enrollgate.common.contract.CourseCapacityPort.CourseSnapshot;
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
class PessimisticLockCourseCapacityAdapterTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private PessimisticLockCourseCapacityAdapter adapter;

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
    void getSnapshot_returnsCourseInfo() {
        when(courseRepository.findById(100L)).thenReturn(Optional.of(course(3, 1)));

        CourseSnapshot snapshot = adapter.getSnapshot(100L);

        assertThat(snapshot.capacity()).isEqualTo(3);
        assertThat(snapshot.currentEnrolledCount()).isEqualTo(1);
        assertThat(snapshot.remainingSeats()).isEqualTo(2);
    }

    @Test
    void getSnapshot_throws_whenCourseNotFound() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.getSnapshot(999L)).isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void attemptReservation_succeeds_whenSeatsAvailable() {
        Course course = course(3, 0);
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));

        var attempt = adapter.attemptReservation(100L);

        assertThat(attempt.reserved()).isTrue();
        assertThat(course.getCurrentEnrolledCount()).isEqualTo(1);
    }

    @Test
    void attemptReservation_fails_whenFull() {
        Course course = course(1, 1);
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));

        var attempt = adapter.attemptReservation(100L);

        assertThat(attempt.reserved()).isFalse();
        assertThat(course.getCurrentEnrolledCount()).isEqualTo(1);
    }

    @Test
    void releaseSeatOrElse_decrements_whenNoPromotion() {
        Course course = course(3, 1);
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));

        adapter.releaseSeatOrElse(100L, () -> false);

        assertThat(course.getCurrentEnrolledCount()).isEqualTo(0);
    }

    @Test
    void releaseSeatOrElse_keepsCount_whenPromoted() {
        Course course = course(3, 1);
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));

        adapter.releaseSeatOrElse(100L, () -> true);

        assertThat(course.getCurrentEnrolledCount()).isEqualTo(1);
    }

    @Test
    void compensateReserve_decrements() {
        Course course = course(3, 1);
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));

        adapter.compensateReserve(100L);

        assertThat(course.getCurrentEnrolledCount()).isEqualTo(0);
    }
}
