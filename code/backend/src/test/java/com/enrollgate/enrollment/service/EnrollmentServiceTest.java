package com.enrollgate.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enrollgate.course.domain.Course;
import com.enrollgate.course.repository.CourseRepository;
import com.enrollgate.enrollment.domain.Enrollment;
import com.enrollgate.enrollment.queue.domain.WaitingQueueEntry;
import com.enrollgate.enrollment.queue.domain.WaitingQueueStatus;
import com.enrollgate.enrollment.queue.service.NoActiveQueueEntryException;
import com.enrollgate.enrollment.queue.service.QueueConfirmExpiredException;
import com.enrollgate.enrollment.queue.service.QueueService;
import com.enrollgate.enrollment.repository.EnrollmentRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private QueueService queueService;

    @InjectMocks
    private EnrollmentService enrollmentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(enrollmentService, "confirmWindowSeconds", 60L);
    }

    private Course courseWithCapacity(int capacity, int currentEnrolledCount) {
        Course course = Course.builder()
                .courseCode("CSE401")
                .name("데이터베이스시스템")
                .professorName("김OO")
                .department("CSE")
                .credit(3)
                .capacity(capacity)
                .semester("2026-2")
                .enrollmentStartAt(LocalDateTime.now().minusDays(1))
                .enrollmentEndAt(LocalDateTime.now().plusDays(1))
                .build();
        ReflectionTestUtils.setField(course, "id", 100L);
        for (int i = 0; i < currentEnrolledCount; i++) {
            course.increaseEnrolledCount();
        }
        return course;
    }

    private WaitingQueueEntry waitingEntry(Long id, Long userId, Long courseId) {
        WaitingQueueEntry entry = WaitingQueueEntry.builder().userId(userId).courseId(courseId).build();
        ReflectionTestUtils.setField(entry, "id", id);
        ReflectionTestUtils.setField(entry, "enteredAt", LocalDateTime.now());
        return entry;
    }

    private WaitingQueueEntry notifiedEntry(Long id, Long userId, Long courseId, LocalDateTime expiresAt) {
        WaitingQueueEntry entry = waitingEntry(id, userId, courseId);
        entry.notifyTurn(Duration.ofSeconds(60));
        ReflectionTestUtils.setField(entry, "expiresAt", expiresAt);
        return entry;
    }

    @Test
    void enroll_succeeds_whenSeatsAvailable() {
        Course course = courseWithCapacity(3, 0);
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByUserIdAndCourseId(1L, 100L)).thenReturn(false);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> {
            Enrollment enrollment = invocation.getArgument(0);
            ReflectionTestUtils.setField(enrollment, "id", 500L);
            return enrollment;
        });

        EnrollmentOutcome outcome = enrollmentService.enroll(1L, 100L);

        assertThat(outcome).isInstanceOf(EnrollmentOutcome.Enrolled.class);
        assertThat(course.getCurrentEnrolledCount()).isEqualTo(1);
    }

    @Test
    void enroll_queues_whenCourseFull() {
        Course course = courseWithCapacity(1, 1);
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByUserIdAndCourseId(2L, 100L)).thenReturn(false);
        WaitingQueueEntry entry = waitingEntry(1L, 2L, 100L);
        when(queueService.enter(2L, 100L)).thenReturn(entry);
        when(queueService.position(entry)).thenReturn(1L);

        EnrollmentOutcome outcome = enrollmentService.enroll(2L, 100L);

        assertThat(outcome).isInstanceOf(EnrollmentOutcome.Queued.class);
        assertThat(((EnrollmentOutcome.Queued) outcome).queuePosition()).isEqualTo(1L);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void enroll_throws_whenAlreadyEnrolled_evenIfPreviouslyCancelled() {
        Course course = courseWithCapacity(3, 0);
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));
        when(enrollmentRepository.existsByUserIdAndCourseId(1L, 100L)).thenReturn(true);

        assertThatThrownBy(() -> enrollmentService.enroll(1L, 100L))
                .isInstanceOf(AlreadyEnrolledException.class);
    }

    @Test
    void enroll_throws_whenPeriodNotStarted() {
        Course course = Course.builder()
                .courseCode("CSE401").name("n").professorName("p").department("d").credit(3).capacity(10)
                .semester("2026-2")
                .enrollmentStartAt(LocalDateTime.now().plusDays(1))
                .enrollmentEndAt(LocalDateTime.now().plusDays(2))
                .build();
        ReflectionTestUtils.setField(course, "id", 100L);
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));

        assertThatThrownBy(() -> enrollmentService.enroll(1L, 100L))
                .isInstanceOf(EnrollmentPeriodClosedException.class);
    }

    @Test
    void enroll_throws_whenCourseNotFound() {
        when(courseRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.enroll(1L, 999L))
                .isInstanceOf(CourseNotFoundException.class);
    }

    @Test
    void cancel_decreasesCount_whenNoOneWaiting() {
        Course course = courseWithCapacity(3, 1);
        Enrollment enrollment = Enrollment.builder().userId(1L).courseId(100L).build();
        ReflectionTestUtils.setField(enrollment, "id", 500L);

        when(enrollmentRepository.findById(500L)).thenReturn(Optional.of(enrollment));
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));
        when(queueService.promoteNext(eq(100L), any())).thenReturn(Optional.empty());

        enrollmentService.cancel(1L, 500L);

        assertThat(course.getCurrentEnrolledCount()).isEqualTo(0);
    }

    @Test
    void cancel_promotesNextWaitingEntry_withoutChangingCount() {
        Course course = courseWithCapacity(3, 1);
        Enrollment enrollment = Enrollment.builder().userId(1L).courseId(100L).build();
        ReflectionTestUtils.setField(enrollment, "id", 500L);
        WaitingQueueEntry promoted = waitingEntry(2L, 9L, 100L);

        when(enrollmentRepository.findById(500L)).thenReturn(Optional.of(enrollment));
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));
        when(queueService.promoteNext(eq(100L), any())).thenReturn(Optional.of(promoted));

        enrollmentService.cancel(1L, 500L);

        assertThat(course.getCurrentEnrolledCount()).isEqualTo(1);
    }

    @Test
    void cancel_throws_whenCallerIsNotOwner() {
        Enrollment enrollment = Enrollment.builder().userId(1L).courseId(100L).build();
        ReflectionTestUtils.setField(enrollment, "id", 500L);
        when(enrollmentRepository.findById(500L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.cancel(2L, 500L))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    @Test
    void cancel_throws_whenAlreadyCancelled() {
        Enrollment enrollment = Enrollment.builder().userId(1L).courseId(100L).build();
        ReflectionTestUtils.setField(enrollment, "id", 500L);
        enrollment.cancel();
        when(enrollmentRepository.findById(500L)).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> enrollmentService.cancel(1L, 500L))
                .isInstanceOf(EnrollmentAlreadyCancelledException.class);
    }

    @Test
    void confirmQueue_succeeds_whenNotExpired() {
        Course course = courseWithCapacity(3, 1);
        WaitingQueueEntry entry = notifiedEntry(1L, 2L, 100L, LocalDateTime.now().plusSeconds(30));
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));
        when(queueService.findNotified(2L, 100L)).thenReturn(Optional.of(entry));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> {
            Enrollment enrollment = invocation.getArgument(0);
            ReflectionTestUtils.setField(enrollment, "id", 501L);
            return enrollment;
        });

        EnrollmentOutcome.Enrolled result = enrollmentService.confirmQueue(2L, 100L);

        assertThat(result.enrollmentId()).isEqualTo(501L);
        assertThat(entry.getStatus()).isEqualTo(WaitingQueueStatus.CONFIRMED);
        // 확정 시점에는 이미 NOTIFIED 승격 때 카운트가 반영돼 있으므로 추가 증가는 없다
        assertThat(course.getCurrentEnrolledCount()).isEqualTo(1);
    }

    @Test
    void confirmQueue_throwsAndPromotesNext_whenExpired() {
        Course course = courseWithCapacity(3, 1);
        WaitingQueueEntry entry = notifiedEntry(1L, 2L, 100L, LocalDateTime.now().minusSeconds(5));
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));
        when(queueService.findNotified(2L, 100L)).thenReturn(Optional.of(entry));
        when(queueService.promoteNext(eq(100L), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.confirmQueue(2L, 100L))
                .isInstanceOf(QueueConfirmExpiredException.class);

        assertThat(entry.getStatus()).isEqualTo(WaitingQueueStatus.EXPIRED);
        assertThat(course.getCurrentEnrolledCount()).isEqualTo(0);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void confirmQueue_throws_whenNoNotifiedEntry() {
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(courseWithCapacity(3, 0)));
        when(queueService.findNotified(2L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.confirmQueue(2L, 100L))
                .isInstanceOf(NoActiveQueueEntryException.class);
    }

    @Test
    void leaveQueue_fromWaiting_doesNotTouchCourseCount() {
        Course course = courseWithCapacity(3, 1);
        WaitingQueueEntry entry = waitingEntry(1L, 2L, 100L);
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));
        when(queueService.findActive(2L, 100L)).thenReturn(Optional.of(entry));

        enrollmentService.leaveQueue(2L, 100L);

        assertThat(entry.getStatus()).isEqualTo(WaitingQueueStatus.CANCELLED);
        assertThat(course.getCurrentEnrolledCount()).isEqualTo(1);
        verify(queueService, never()).promoteNext(anyLong(), any());
    }

    @Test
    void leaveQueue_fromNotified_releasesReservedSeat() {
        Course course = courseWithCapacity(3, 1);
        WaitingQueueEntry entry = notifiedEntry(1L, 2L, 100L, LocalDateTime.now().plusSeconds(30));
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));
        when(queueService.findActive(2L, 100L)).thenReturn(Optional.of(entry));
        when(queueService.promoteNext(eq(100L), any())).thenReturn(Optional.empty());

        enrollmentService.leaveQueue(2L, 100L);

        assertThat(entry.getStatus()).isEqualTo(WaitingQueueStatus.CANCELLED);
        assertThat(course.getCurrentEnrolledCount()).isEqualTo(0);
    }

    @Test
    void queueStatus_waiting_returnsPositionAndEstimate() {
        WaitingQueueEntry entry = waitingEntry(1L, 2L, 100L);
        when(queueService.findActive(2L, 100L)).thenReturn(Optional.of(entry));
        when(queueService.position(entry)).thenReturn(3L);

        QueueStatusResult result = enrollmentService.queueStatus(2L, 100L);

        assertThat(result.status()).isEqualTo("WAITING");
        assertThat(result.position()).isEqualTo(3L);
    }

    @Test
    void queueStatus_notified_returnsConfirmDeadline() {
        LocalDateTime deadline = LocalDateTime.now().plusSeconds(45);
        WaitingQueueEntry entry = notifiedEntry(1L, 2L, 100L, deadline);
        when(queueService.findActive(2L, 100L)).thenReturn(Optional.of(entry));

        QueueStatusResult result = enrollmentService.queueStatus(2L, 100L);

        assertThat(result.status()).isEqualTo("NOTIFIED");
        assertThat(result.confirmDeadline()).isEqualTo(deadline);
    }

    @Test
    void queueStatus_throws_whenNoActiveEntry() {
        when(queueService.findActive(2L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.queueStatus(2L, 100L))
                .isInstanceOf(NoActiveQueueEntryException.class);
    }

    @Test
    void expireOverdueQueueEntries_expiresAndPromotesEach() {
        Course course = courseWithCapacity(3, 1);
        WaitingQueueEntry overdue = notifiedEntry(1L, 2L, 100L, LocalDateTime.now().minusSeconds(1));
        when(queueService.findExpiredNotified(any())).thenReturn(java.util.List.of(overdue));
        when(courseRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(course));
        when(queueService.promoteNext(eq(100L), any())).thenReturn(Optional.empty());

        enrollmentService.expireOverdueQueueEntries();

        assertThat(overdue.getStatus()).isEqualTo(WaitingQueueStatus.EXPIRED);
        assertThat(course.getCurrentEnrolledCount()).isEqualTo(0);
    }
}
