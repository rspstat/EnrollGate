package com.enrollgate.enrollment.queue.service;

import com.enrollgate.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AlreadyQueuedException extends BusinessException {

    public AlreadyQueuedException(Long userId, Long courseId) {
        super("ALREADY_QUEUED", HttpStatus.CONFLICT,
                "이미 대기열에 진입해 있습니다: userId=" + userId + ", courseId=" + courseId);
    }
}
