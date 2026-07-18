package com.enrollgate.course.controller;

import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

/**
 * 관리자 부분 수정 요청. 필드를 null로 두면 해당 값은 변경하지 않는다.
 */
public record UpdateCourseRequest(
        String name,
        String professorName,
        String department,
        Integer credit,
        @Positive Integer capacity,
        LocalDateTime enrollmentStartAt,
        LocalDateTime enrollmentEndAt
) {
}
