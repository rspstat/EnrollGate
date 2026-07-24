package com.enrollgate.enrollment.queue.controller;

import com.enrollgate.enrollment.controller.EnrollResponse;
import com.enrollgate.enrollment.service.EnrollmentOutcome;
import com.enrollgate.enrollment.service.EnrollmentService;
import com.enrollgate.enrollment.service.QueueStatusResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QueueController {

    private final EnrollmentService enrollmentService;

    @PostMapping("/api/v1/courses/{courseId}/queue/confirm")
    public ResponseEntity<EnrollResponse> confirm(@PathVariable Long courseId, @AuthenticationPrincipal Long userId) {
        EnrollmentOutcome.Enrolled result = enrollmentService.confirmQueue(userId, courseId);
        return ResponseEntity.status(HttpStatus.CREATED).body(EnrollResponse.enrolled(result));
    }

    @DeleteMapping("/api/v1/courses/{courseId}/queue")
    public ResponseEntity<Void> leave(@PathVariable Long courseId, @AuthenticationPrincipal Long userId) {
        enrollmentService.leaveQueue(userId, courseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/courses/{courseId}/queue/status")
    public ResponseEntity<QueueStatusResult> status(@PathVariable Long courseId, @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(enrollmentService.queueStatus(userId, courseId));
    }
}
