package com.enrollgate.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
class CourseCommandServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseCommandService courseCommandService;

    private Course course(Long id, int capacity, int currentEnrolledCount) {
        Course course = Course.builder()
                .courseCode("CSE401").name("데이터베이스시스템").professorName("김OO").department("CSE")
                .credit(3).capacity(capacity).semester("2026-2")
                .enrollmentStartAt(LocalDateTime.now().minusDays(1))
                .enrollmentEndAt(LocalDateTime.now().plusDays(1))
                .build();
        ReflectionTestUtils.setField(course, "id", id);
        for (int i = 0; i < currentEnrolledCount; i++) {
            course.increaseEnrolledCount();
        }
        return course;
    }

    @Test
    void update_appliesOnlyNonNullFields() {
        Course course = course(1L, 40, 10);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));

        courseCommandService.update(1L, "새 과목명", null, null, null, null, null, null);

        assertThat(course.getName()).isEqualTo("새 과목명");
        assertThat(course.getProfessorName()).isEqualTo("김OO");
        assertThat(course.getCapacity()).isEqualTo(40);
    }

    @Test
    void update_changesCapacity_whenProvidedAndValid() {
        Course course = course(1L, 40, 10);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));

        courseCommandService.update(1L, null, null, null, null, 50, null, null);

        assertThat(course.getCapacity()).isEqualTo(50);
    }

    @Test
    void update_throws_whenNewCapacityBelowCurrentEnrolledCount() {
        Course course = course(1L, 40, 10);
        when(courseRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> courseCommandService.update(1L, null, null, null, null, 5, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_throws_whenCourseNotFound() {
        when(courseRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseCommandService.update(999L, "n", null, null, null, null, null, null))
                .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void create_persistsCourseWithZeroEnrolledCount() {
        when(courseRepository.save(org.mockito.ArgumentMatchers.any(Course.class))).thenAnswer(invocation -> {
            Course course = invocation.getArgument(0);
            ReflectionTestUtils.setField(course, "id", 42L);
            return course;
        });

        Long courseId = courseCommandService.create("CSE401", "데이터베이스시스템", "김OO", "CSE", 3, 40, "2026-2",
                LocalDateTime.now(), LocalDateTime.now().plusDays(30));

        assertThat(courseId).isEqualTo(42L);
    }
}
