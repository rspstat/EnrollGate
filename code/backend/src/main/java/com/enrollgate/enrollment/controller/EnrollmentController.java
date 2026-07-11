package com.enrollgate.enrollment.controller;

import com.enrollgate.enrollment.service.EnrollmentResult;
import com.enrollgate.enrollment.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping("/api/v1/courses/{courseId}/enroll")
    public ResponseEntity<EnrollResponse> enroll(@PathVariable Long courseId, @AuthenticationPrincipal Long userId) {
        EnrollmentResult result = enrollmentService.enroll(userId, courseId);
        return ResponseEntity.status(HttpStatus.CREATED).body(EnrollResponse.from(result));
    }
}
