package com.enrollgate.enrollment.service;

import com.enrollgate.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EnrollmentAlreadyCancelledException extends BusinessException {

    public EnrollmentAlreadyCancelledException(Long enrollmentId) {
        super("ENROLLMENT_ALREADY_CANCELLED", HttpStatus.CONFLICT, "이미 취소된 신청입니다: enrollmentId=" + enrollmentId);
    }
}
