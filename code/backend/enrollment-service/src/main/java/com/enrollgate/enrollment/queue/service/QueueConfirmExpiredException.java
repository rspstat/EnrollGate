package com.enrollgate.enrollment.queue.service;

import com.enrollgate.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class QueueConfirmExpiredException extends BusinessException {

    public QueueConfirmExpiredException(Long courseId) {
        super("QUEUE_CONFIRM_EXPIRED", HttpStatus.CONFLICT,
                "확정 시간이 초과되어 대기열 순번이 만료되었습니다: courseId=" + courseId);
    }
}
