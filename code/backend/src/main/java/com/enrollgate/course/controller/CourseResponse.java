package com.enrollgate.course.controller;

import com.enrollgate.course.service.CourseSummary;

public record CourseResponse(
        Long id,
        String courseCode,
        String name,
        String professorName,
        String department,
        Integer credit,
        Integer capacity,
        Integer currentEnrolledCount,
        Integer remainingSeats,
        String semester,
        Long queueLength
) {

    public static CourseResponse from(CourseSummary summary) {
        return new CourseResponse(
                summary.id(),
                summary.courseCode(),
                summary.name(),
                summary.professorName(),
                summary.department(),
                summary.credit(),
                summary.capacity(),
                summary.currentEnrolledCount(),
                summary.remainingSeats(),
                summary.semester(),
                summary.queueLength());
    }
}
