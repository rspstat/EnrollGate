package com.enrollgate.user.service;

import com.enrollgate.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class DuplicateEmailException extends BusinessException {

    public DuplicateEmailException(String email) {
        super("DUPLICATE_EMAIL", HttpStatus.CONFLICT, "이미 가입된 이메일입니다: " + email);
    }
}
