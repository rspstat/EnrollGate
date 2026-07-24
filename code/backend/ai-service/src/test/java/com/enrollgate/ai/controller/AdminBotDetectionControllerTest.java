package com.enrollgate.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.enrollgate.ai.domain.BotDetectionLog;
import com.enrollgate.ai.repository.BotDetectionLogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AdminBotDetectionControllerTest {

    @Mock
    private BotDetectionLogRepository botDetectionLogRepository;

    @InjectMocks
    private AdminBotDetectionController controller;

    @Test
    void logs_returnsLatestEntriesMappedToResponse() {
        BotDetectionLog log = BotDetectionLog.builder()
                .userId(1L).courseId(100L)
                .requestFeatures("{\"outcome\":\"ENROLLED\"}")
                .suspicionScore(0.8)
                .actionTaken("FLAGGED")
                .build();
        ReflectionTestUtils.setField(log, "id", 5L);
        when(botDetectionLogRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(log));

        List<BotDetectionLogResponse> result = controller.logs();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(5L);
        assertThat(result.get(0).actionTaken()).isEqualTo("FLAGGED");
    }
}
