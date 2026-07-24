package com.enrollgate.enrollment.queue.service;

import com.enrollgate.common.contract.QueueLengthPort;
import com.enrollgate.enrollment.queue.domain.WaitingQueueStatus;
import com.enrollgate.enrollment.queue.repository.WaitingQueueRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** course-service가 참조하는 {@link QueueLengthPort}의 실제 구현. */
@Component
@RequiredArgsConstructor
public class QueueLengthAdapter implements QueueLengthPort {

    private static final List<WaitingQueueStatus> ACTIVE_QUEUE_STATUSES =
            List.of(WaitingQueueStatus.WAITING, WaitingQueueStatus.NOTIFIED);

    private final WaitingQueueRepository waitingQueueRepository;

    @Override
    public long queueLength(Long courseId) {
        return waitingQueueRepository.countByCourseIdAndStatusIn(courseId, ACTIVE_QUEUE_STATUSES);
    }
}
