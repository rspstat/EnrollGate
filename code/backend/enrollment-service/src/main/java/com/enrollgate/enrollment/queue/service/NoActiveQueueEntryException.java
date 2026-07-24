package com.enrollgate.enrollment.queue.service;

import com.enrollgate.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class NoActiveQueueEntryException extends BusinessException {

    public NoActiveQueueEntryException(Long userId, Long courseId) {
        super("QUEUE_ENTRY_NOT_FOUND", HttpStatus.NOT_FOUND,
                "진행 중인 대기열 항목이 없습니다: userId=" + userId + ", courseId=" + courseId);
    }
}
