package com.enrollgate.course.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enrollgate.common.contract.QueueLengthPort;
import com.enrollgate.course.domain.Course;
import com.enrollgate.course.repository.CourseRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CourseQueryServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private QueueLengthPort queueLengthPort;

    @InjectMocks
    private CourseQueryService courseQueryService;

    private Course course(Long id) {
        Course course = Course.builder()
                .courseCode("CSE401").name("데이터베이스시스템").professorName("김OO").department("CSE")
                .credit(3).capacity(40).semester("2026-2")
                .enrollmentStartAt(LocalDateTime.now().minusDays(1))
                .enrollmentEndAt(LocalDateTime.now().plusDays(1))
                .build();
        ReflectionTestUtils.setField(course, "id", id);
        return course;
    }

    @Test
    void list_usesSemesterAndDepartmentFilter_whenBothProvided() {
        when(courseRepository.findBySemesterAndDepartment("2026-2", "CSE")).thenReturn(List.of(course(1L)));
        when(queueLengthPort.queueLength(any())).thenReturn(0L);

        List<CourseSummary> result = courseQueryService.list("2026-2", "CSE");

        assertThat(result).hasSize(1);
        verify(courseRepository).findBySemesterAndDepartment("2026-2", "CSE");
    }

    @Test
    void list_usesFindAll_whenNoFilterProvided() {
        when(courseRepository.findAll()).thenReturn(List.of(course(1L), course(2L)));
        when(queueLengthPort.queueLength(any())).thenReturn(0L);

        List<CourseSummary> result = courseQueryService.list(null, null);

        assertThat(result).hasSize(2);
        verify(courseRepository).findAll();
    }

    @Test
    void detail_includesQueueLength() {
        when(courseRepository.findById(1L)).thenReturn(Optional.of(course(1L)));
        when(queueLengthPort.queueLength(any())).thenReturn(5L);

        CourseSummary summary = courseQueryService.detail(1L);

        assertThat(summary.queueLength()).isEqualTo(5L);
        assertThat(summary.remainingSeats()).isEqualTo(40);
    }

    @Test
    void detail_throws_whenCourseNotFound() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseQueryService.detail(999L))
                .isInstanceOf(CourseNotFoundException.class);
    }
}
