package com.enrollgate.enrollment.service;

import com.enrollgate.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * 정원 초과 시 원래는 대기열(202 QUEUED, docs/EnrollGate-ERD-API-Spec.md 3.3)로 안내해야 하지만,
 * 대기열 기능은 아직 구현되지 않아 임시로 즉시 실패 처리한다. 대기열 구현 시 이 예외 대신
 * QUEUED 응답 분기로 대체될 예정.
 */
public class CourseFullException extends BusinessException {

    public CourseFullException(Long courseId) {
        super("COURSE_FULL", HttpStatus.CONFLICT, "정원이 초과되었습니다: courseId=" + courseId);
    }
}
