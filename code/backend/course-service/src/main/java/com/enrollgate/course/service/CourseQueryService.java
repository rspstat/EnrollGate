package com.enrollgate.course.service;

import com.enrollgate.common.contract.QueueLengthPort;
import com.enrollgate.course.domain.Course;
import com.enrollgate.course.repository.CourseRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseQueryService {

    private final CourseRepository courseRepository;
    private final QueueLengthPort queueLengthPort;

    @Transactional(readOnly = true)
    public List<CourseSummary> list(String semester, String department) {
        List<Course> courses;
        if (semester != null && department != null) {
            courses = courseRepository.findBySemesterAndDepartment(semester, department);
        } else if (semester != null) {
            courses = courseRepository.findBySemester(semester);
        } else if (department != null) {
            courses = courseRepository.findByDepartment(department);
        } else {
            courses = courseRepository.findAll();
        }
        return courses.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public CourseSummary detail(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        return toSummary(course);
    }

    private CourseSummary toSummary(Course course) {
        long queueLength = queueLengthPort.queueLength(course.getId());
        return CourseSummary.of(course, queueLength);
    }
}
