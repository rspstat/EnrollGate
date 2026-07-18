package com.enrollgate.course.service;

import com.enrollgate.course.domain.Course;
import com.enrollgate.course.repository.CourseRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseCommandService {

    private final CourseRepository courseRepository;

    @Transactional
    public Long create(String courseCode, String name, String professorName, String department, Integer credit,
                        Integer capacity, String semester, LocalDateTime enrollmentStartAt, LocalDateTime enrollmentEndAt) {
        Course course = Course.builder()
                .courseCode(courseCode)
                .name(name)
                .professorName(professorName)
                .department(department)
                .credit(credit)
                .capacity(capacity)
                .semester(semester)
                .enrollmentStartAt(enrollmentStartAt)
                .enrollmentEndAt(enrollmentEndAt)
                .build();
        return courseRepository.save(course).getId();
    }

    @Transactional
    public void update(Long courseId, String name, String professorName, String department, Integer credit,
                        Integer capacity, LocalDateTime enrollmentStartAt, LocalDateTime enrollmentEndAt) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        course.updateDetails(name, professorName, department, credit, enrollmentStartAt, enrollmentEndAt);
        if (capacity != null) {
            course.updateCapacity(capacity);
        }
    }
}
