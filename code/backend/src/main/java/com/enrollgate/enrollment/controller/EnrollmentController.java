package com.enrollgate.enrollment.controller;

import com.enrollgate.enrollment.service.EnrollmentOutcome;
import com.enrollgate.enrollment.service.EnrollmentService;
import java.util.List;
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
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping("/api/v1/courses/{courseId}/enroll")
    public ResponseEntity<EnrollResponse> enroll(@PathVariable Long courseId, @AuthenticationPrincipal Long userId) {
        EnrollmentOutcome outcome = enrollmentService.enroll(userId, courseId);
        return switch (outcome) {
            case EnrollmentOutcome.Enrolled enrolled ->
                    ResponseEntity.status(HttpStatus.CREATED).body(EnrollResponse.enrolled(enrolled));
            case EnrollmentOutcome.Queued queued ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(EnrollResponse.queued(courseId, queued));
        };
    }

    @DeleteMapping("/api/v1/enrollments/{enrollmentId}")
    public ResponseEntity<Void> cancel(@PathVariable Long enrollmentId, @AuthenticationPrincipal Long userId) {
        enrollmentService.cancel(userId, enrollmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/enrollments/me")
    public ResponseEntity<List<EnrollmentSummaryResponse>> myEnrollments(@AuthenticationPrincipal Long userId) {
        List<EnrollmentSummaryResponse> response = enrollmentService.listMine(userId).stream()
                .map(EnrollmentSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }
}
