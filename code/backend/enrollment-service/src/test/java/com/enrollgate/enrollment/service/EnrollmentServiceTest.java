package com.enrollgate.enrollment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enrollgate.common.contract.CourseCapacityPort;
import com.enrollgate.common.contract.CourseCapacityPort.CourseSnapshot;
import com.enrollgate.enrollment.domain.Enrollment;
import com.enrollgate.enrollment.domain.EnrollmentStatus;
import com.enrollgate.enrollment.queue.domain.WaitingQueueEntry;
import com.enrollgate.enrollment.queue.domain.WaitingQueueStatus;
import com.enrollgate.enrollment.queue.service.NoActiveQueueEntryException;
import com.enrollgate.enrollment.queue.service.QueueConfirmExpiredException;
import com.enrollgate.enrollment.queue.service.QueueService;
import com.enrollgate.enrollment.queue.websocket.QueueNotificationService;
import com.enrollgate.enrollment.repository.EnrollmentRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private QueueService queueService;

    @Mock
    private CourseCapacityPort courseCapacityPort;

    @Mock
    private QueueNotificationService queueNotificationService;

    @InjectMocks
    private EnrollmentService enrollmentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(enrollmentService, "confirmWindowSeconds", 60L);
    }

    private CourseSnapshot snapshot(int capacity, int currentEnrolledCount) {
        return new CourseSnapshot(100L, "CSE401", "데이터베이스시스템", capacity, currentEnrolledCount,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
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

    /** releaseSeatOrElse 목(mock) 호출 시 실제 어댑터처럼 콜백을 실행하도록 스텁한다. */
    private void stubReleaseSeatOrElseInvokesCallback(Long courseId) {
        doAnswer(invocation -> {
            Supplier<Boolean> callback = invocation.getArgument(1);
            callback.get();
            return null;
        }).when(courseCapacityPort).releaseSeatOrElse(eq(courseId), any());
    }

    @Test
    void enroll_succeeds_whenSeatsAvailable() {
        when(enrollmentRepository.existsByUserIdAndCourseId(1L, 100L)).thenReturn(false);
        when(courseCapacityPort.attemptReservation(100L))
                .thenReturn(new CourseCapacityPort.ReservationAttempt(snapshot(3, 0), true));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> {
            Enrollment enrollment = invocation.getArgument(0);
            ReflectionTestUtils.setField(enrollment, "id", 500L);
            return enrollment;
        });

        EnrollmentOutcome outcome = enrollmentService.enroll(1L, 100L);

        assertThat(outcome).isInstanceOf(EnrollmentOutcome.Enrolled.class);
    }

    @Test
    void enroll_queues_whenCourseFull() {
        when(enrollmentRepository.existsByUserIdAndCourseId(2L, 100L)).thenReturn(false);
        when(courseCapacityPort.attemptReservation(100L))
                .thenReturn(new CourseCapacityPort.ReservationAttempt(snapshot(1, 1), false));
        when(queueService.enterQueue(2L, 100L)).thenReturn(new EnrollmentOutcome.Queued(1L, 15L));

        EnrollmentOutcome outcome = enrollmentService.enroll(2L, 100L);

        assertThat(outcome).isInstanceOf(EnrollmentOutcome.Queued.class);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void enroll_throws_whenAlreadyEnrolled_evenIfPreviouslyCancelled() {
        when(enrollmentRepository.existsByUserIdAndCourseId(1L, 100L)).thenReturn(true);

        assertThatThrownBy(() -> enrollmentService.enroll(1L, 100L))
                .isInstanceOf(AlreadyEnrolledException.class);
        verify(courseCapacityPort, never()).attemptReservation(any());
    }

    @Test
    void enroll_throwsAndCompensates_whenPeriodNotStarted() {
        when(enrollmentRepository.existsByUserIdAndCourseId(1L, 100L)).thenReturn(false);
        CourseSnapshot snapshot = new CourseSnapshot(100L, "CSE401", "n", 10, 0,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(2));
        when(courseCapacityPort.attemptReservation(100L))
                .thenReturn(new CourseCapacityPort.ReservationAttempt(snapshot, true));

        assertThatThrownBy(() -> enrollmentService.enroll(1L, 100L))
                .isInstanceOf(EnrollmentPeriodClosedException.class);

        verify(courseCapacityPort).compensateReserve(100L);
    }

    @Test
    void enroll_compensatesAndThrowsAlreadyEnrolled_onConcurrentDuplicateInsert() {
        when(enrollmentRepository.existsByUserIdAndCourseId(1L, 100L)).thenReturn(false);
        when(courseCapacityPort.attemptReservation(100L))
                .thenReturn(new CourseCapacityPort.ReservationAttempt(snapshot(3, 0), true));
        when(enrollmentRepository.save(any(Enrollment.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertThatThrownBy(() -> enrollmentService.enroll(1L, 100L))
                .isInstanceOf(AlreadyEnrolledException.class);

        verify(courseCapacityPort).compensateReserve(100L);
    }

    @Test
    void cancel_releasesSeat_delegatingToPort() {
        Enrollment enrollment = Enrollment.builder().userId(1L).courseId(100L).build();
        ReflectionTestUtils.setField(enrollment, "id", 500L);
        when(enrollmentRepository.findById(500L)).thenReturn(Optional.of(enrollment));
        when(queueService.promoteNext(eq(100L), any())).thenReturn(Optional.empty());
        stubReleaseSeatOrElseInvokesCallback(100L);

        enrollmentService.cancel(1L, 500L);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        verify(courseCapacityPort).releaseSeatOrElse(eq(100L), any());
        verify(queueService).promoteNext(eq(100L), any());
    }

    @Test
    void cancel_notifiesPromotedUser_whenSomeoneWasWaiting() {
        Enrollment enrollment = Enrollment.builder().userId(1L).courseId(100L).build();
        ReflectionTestUtils.setField(enrollment, "id", 500L);
        WaitingQueueEntry promoted = waitingEntry(2L, 9L, 100L);
        promoted.notifyTurn(Duration.ofSeconds(60));

        when(enrollmentRepository.findById(500L)).thenReturn(Optional.of(enrollment));
        when(queueService.promoteNext(eq(100L), any())).thenReturn(Optional.of(promoted));
        stubReleaseSeatOrElseInvokesCallback(100L);

        enrollmentService.cancel(1L, 500L);

        verify(queueNotificationService).notifyYourTurn(eq(100L), eq(9L), any());
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
        WaitingQueueEntry entry = notifiedEntry(1L, 2L, 100L, LocalDateTime.now().plusSeconds(30));
        when(queueService.findNotified(2L, 100L)).thenReturn(Optional.of(entry));
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(invocation -> {
            Enrollment enrollment = invocation.getArgument(0);
            ReflectionTestUtils.setField(enrollment, "id", 501L);
            return enrollment;
        });

        EnrollmentOutcome.Enrolled result = enrollmentService.confirmQueue(2L, 100L);

        assertThat(result.enrollmentId()).isEqualTo(501L);
        assertThat(entry.getStatus()).isEqualTo(WaitingQueueStatus.CONFIRMED);
        verify(courseCapacityPort, never()).releaseSeatOrElse(any(), any());
    }

    @Test
    void confirmQueue_throwsAndReleasesSeat_whenExpired() {
        WaitingQueueEntry entry = notifiedEntry(1L, 2L, 100L, LocalDateTime.now().minusSeconds(5));
        when(queueService.findNotified(2L, 100L)).thenReturn(Optional.of(entry));
        when(queueService.promoteNext(eq(100L), any())).thenReturn(Optional.empty());
        stubReleaseSeatOrElseInvokesCallback(100L);

        assertThatThrownBy(() -> enrollmentService.confirmQueue(2L, 100L))
                .isInstanceOf(QueueConfirmExpiredException.class);

        assertThat(entry.getStatus()).isEqualTo(WaitingQueueStatus.EXPIRED);
        verify(courseCapacityPort).releaseSeatOrElse(eq(100L), any());
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void confirmQueue_throws_whenNoNotifiedEntry() {
        when(queueService.findNotified(2L, 100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> enrollmentService.confirmQueue(2L, 100L))
                .isInstanceOf(NoActiveQueueEntryException.class);
    }

    @Test
    void leaveQueue_fromWaiting_doesNotTouchCourseCapacity() {
        WaitingQueueEntry entry = waitingEntry(1L, 2L, 100L);
        when(queueService.findActive(2L, 100L)).thenReturn(Optional.of(entry));

        enrollmentService.leaveQueue(2L, 100L);

        assertThat(entry.getStatus()).isEqualTo(WaitingQueueStatus.CANCELLED);
        verify(courseCapacityPort, never()).releaseSeatOrElse(any(), any());
    }

    @Test
    void leaveQueue_fromNotified_releasesReservedSeat() {
        WaitingQueueEntry entry = notifiedEntry(1L, 2L, 100L, LocalDateTime.now().plusSeconds(30));
        when(queueService.findActive(2L, 100L)).thenReturn(Optional.of(entry));
        when(queueService.promoteNext(eq(100L), any())).thenReturn(Optional.empty());
        stubReleaseSeatOrElseInvokesCallback(100L);

        enrollmentService.leaveQueue(2L, 100L);

        assertThat(entry.getStatus()).isEqualTo(WaitingQueueStatus.CANCELLED);
        verify(courseCapacityPort).releaseSeatOrElse(eq(100L), any());
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
    void expireOverdueQueueEntries_expiresAndReleasesEach() {
        WaitingQueueEntry overdue = notifiedEntry(1L, 2L, 100L, LocalDateTime.now().minusSeconds(1));
        when(queueService.findExpiredNotified(any())).thenReturn(List.of(overdue));
        when(queueService.promoteNext(eq(100L), any())).thenReturn(Optional.empty());
        stubReleaseSeatOrElseInvokesCallback(100L);

        enrollmentService.expireOverdueQueueEntries();

        assertThat(overdue.getStatus()).isEqualTo(WaitingQueueStatus.EXPIRED);
        verify(courseCapacityPort).releaseSeatOrElse(eq(100L), any());
    }

    @Test
    void listMine_includesCourseInfoFromSnapshot() {
        Enrollment enrollment = Enrollment.builder().userId(1L).courseId(100L).build();
        ReflectionTestUtils.setField(enrollment, "id", 500L);
        when(enrollmentRepository.findAllByUserIdOrderByEnrolledAtDesc(1L)).thenReturn(List.of(enrollment));
        when(courseCapacityPort.getSnapshot(100L)).thenReturn(snapshot(3, 1));

        List<EnrollmentSummary> result = enrollmentService.listMine(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).courseCode()).isEqualTo("CSE401");
    }
}
