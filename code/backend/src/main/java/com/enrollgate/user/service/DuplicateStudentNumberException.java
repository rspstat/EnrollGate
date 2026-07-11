package com.enrollgate.user.service;

import com.enrollgate.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DuplicateStudentNumberException extends BusinessException {

    public DuplicateStudentNumberException(String studentNumber) {
        super("DUPLICATE_STUDENT_NUMBER", HttpStatus.CONFLICT, "이미 등록된 학번입니다: " + studentNumber);
    }
}
