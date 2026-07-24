package com.enrollgate.enrollment.service;

import com.enrollgate.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class EnrollmentNotFoundException extends BusinessException {

    public EnrollmentNotFoundException(Long enrollmentId) {
        super("ENROLLMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "존재하지 않는 신청 내역입니다: enrollmentId=" + enrollmentId);
    }
}
