package com.enrollgate.enrollment.service;

import com.enrollgate.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class CourseNotFoundException extends BusinessException {

    public CourseNotFoundException(Long courseId) {
        super("COURSE_NOT_FOUND", HttpStatus.NOT_FOUND, "존재하지 않는 과목입니다: courseId=" + courseId);
    }
}
