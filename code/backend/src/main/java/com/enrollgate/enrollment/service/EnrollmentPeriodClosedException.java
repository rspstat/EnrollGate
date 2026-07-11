package com.enrollgate.enrollment.service;

import com.enrollgate.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EnrollmentPeriodClosedException extends BusinessException {

    public EnrollmentPeriodClosedException(Long courseId) {
        super("ENROLLMENT_PERIOD_CLOSED", HttpStatus.CONFLICT,
                "신청 기간이 아닙니다: courseId=" + courseId);
    }
}
