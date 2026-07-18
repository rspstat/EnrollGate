package com.enrollgate.enrollment.queue.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.enrollgate.enrollment.queue.domain.WaitingQueueEntry;
import com.enrollgate.enrollment.queue.domain.WaitingQueueStatus;
import com.enrollgate.enrollment.queue.repository.WaitingQueueRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QueueServiceTest {

    @Mock
    private WaitingQueueRepository waitingQueueRepository;

    @InjectMocks
    private QueueService queueService;

    private WaitingQueueEntry entryWithId(Long id, Long userId, Long courseId) {
        WaitingQueueEntry entry = WaitingQueueEntry.builder().userId(userId).courseId(courseId).build();
        ReflectionTestUtils.setField(entry, "id", id);
        ReflectionTestUtils.setField(entry, "enteredAt", LocalDateTime.now());
        return entry;
    }

    @Test
    void enter_throws_whenAlreadyActiveInQueue() {
        when(waitingQueueRepository.existsByUserIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> queueService.enter(1L, 100L))
                .isInstanceOf(AlreadyQueuedException.class);
    }

    @Test
    void enter_savesNewEntry_whenNotAlreadyQueued() {
        when(waitingQueueRepository.existsByUserIdAndCourseIdAndStatusIn(any(), any(), any())).thenReturn(false);
        when(waitingQueueRepository.save(any(WaitingQueueEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WaitingQueueEntry entry = queueService.enter(1L, 100L);

        assertThat(entry.getUserId()).isEqualTo(1L);
        assertThat(entry.getCourseId()).isEqualTo(100L);
        assertThat(entry.getStatus()).isEqualTo(WaitingQueueStatus.WAITING);
    }

    @Test
    void position_isCountOfEarlierWaitingEntriesPlusOne() {
        WaitingQueueEntry entry = entryWithId(5L, 1L, 100L);
        when(waitingQueueRepository.countByCourseIdAndStatusAndEnteredAtBefore(
                eq(100L), eq(WaitingQueueStatus.WAITING), any())).thenReturn(3L);

        long position = queueService.position(entry);

        assertThat(position).isEqualTo(4L);
    }

    @Test
    void promoteNext_notifiesEarliestWaitingEntry() {
        WaitingQueueEntry entry = entryWithId(7L, 2L, 100L);
        when(waitingQueueRepository.findFirstByCourseIdAndStatusOrderByEnteredAtAsc(100L, WaitingQueueStatus.WAITING))
                .thenReturn(Optional.of(entry));

        Optional<WaitingQueueEntry> promoted = queueService.promoteNext(100L, Duration.ofSeconds(60));

        assertThat(promoted).isPresent();
        assertThat(promoted.get().getStatus()).isEqualTo(WaitingQueueStatus.NOTIFIED);
        assertThat(promoted.get().getExpiresAt()).isNotNull();
    }

    @Test
    void promoteNext_returnsEmpty_whenNoOneWaiting() {
        when(waitingQueueRepository.findFirstByCourseIdAndStatusOrderByEnteredAtAsc(anyLong(), any()))
                .thenReturn(Optional.empty());

        Optional<WaitingQueueEntry> promoted = queueService.promoteNext(100L, Duration.ofSeconds(60));

        assertThat(promoted).isEmpty();
    }

    @Test
    void findExpiredNotified_delegatesToRepository() {
        LocalDateTime now = LocalDateTime.now();
        WaitingQueueEntry entry = entryWithId(9L, 3L, 100L);
        when(waitingQueueRepository.findByStatusAndExpiresAtBefore(WaitingQueueStatus.NOTIFIED, now))
                .thenReturn(List.of(entry));

        List<WaitingQueueEntry> expired = queueService.findExpiredNotified(now);

        assertThat(expired).containsExactly(entry);
    }
}
