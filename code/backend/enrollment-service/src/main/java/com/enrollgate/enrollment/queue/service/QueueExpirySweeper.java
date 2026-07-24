package com.enrollgate.enrollment.queue.service;

import com.enrollgate.enrollment.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 확정 대기 시간(queue.confirm-window-seconds) 내에 확정되지 않은 NOTIFIED 항목을 주기적으로 만료 처리하고
 * 다음 대기자에게 순번을 넘긴다. 1단계(DB 폴링 수준) 대기열에서 만료 트리거 방식으로 스케줄러를 채택했다
 * (아키텍처 문서 Open Question: 스케줄러 vs 이벤트 기반 → 스케줄러로 결정).
 */
@Component
@RequiredArgsConstructor
public class QueueExpirySweeper {

    private final EnrollmentService enrollmentService;

    @Scheduled(fixedDelayString = "${queue.expiry-sweep-interval-ms:5000}")
    public void sweep() {
        enrollmentService.expireOverdueQueueEntries();
    }
}
