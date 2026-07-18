package com.enrollgate.course.service;

import com.enrollgate.course.domain.Course;

public record CourseSummary(
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
        long queueLength
) {

    public static CourseSummary of(Course course, long queueLength) {
        return new CourseSummary(
                course.getId(),
                course.getCourseCode(),
                course.getName(),
                course.getProfessorName(),
                course.getDepartment(),
                course.getCredit(),
                course.getCapacity(),
                course.getCurrentEnrolledCount(),
                course.remainingSeats(),
                course.getSemester(),
                queueLength);
    }
}
