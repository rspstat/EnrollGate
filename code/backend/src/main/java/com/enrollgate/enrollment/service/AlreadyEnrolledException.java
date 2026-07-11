package com.enrollgate.enrollment.service;

import com.enrollgate.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AlreadyEnrolledException extends BusinessException {

    public AlreadyEnrolledException(Long userId, Long courseId) {
        super("ALREADY_ENROLLED", HttpStatus.CONFLICT,
                "이미 신청한 과목입니다: userId=" + userId + ", courseId=" + courseId);
    }
}
