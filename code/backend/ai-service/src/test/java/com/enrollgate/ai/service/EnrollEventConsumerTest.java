package com.enrollgate.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.enrollgate.ai.domain.BotDetectionLog;
import com.enrollgate.ai.repository.BotDetectionLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class EnrollEventConsumerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private BotDetectionScorer scorer;

    @Mock
    private BotDetectionLogRepository botDetectionLogRepository;

    private MapRecord<String, Object, Object> record(Map<String, String> fields) {
        Map<Object, Object> content = new LinkedHashMap<>(fields);
        return StreamRecords.newRecord().ofMap(content).withStreamKey("enrollgate:enroll-events");
    }

    @Test
    void processRecord_scoresAndPersistsLog() {
        EnrollEventConsumer consumer = new EnrollEventConsumer(redisTemplate, scorer, botDetectionLogRepository, new ObjectMapper());

        Map<String, String> fields = Map.of(
                "userId", "1",
                "courseId", "100",
                "outcome", "ENROLLED",
                "intervalSeconds", "0.05",
                "repeatedCount1Min", "10",
                "userAgentSuspicious", "true"
        );
        when(scorer.score(any())).thenReturn(new BotDetectionScorer.ScoringResult(0.9, "FLAGGED"));

        consumer.processRecord(record(fields));

        ArgumentCaptor<BotDetectionLog> captor = ArgumentCaptor.forClass(BotDetectionLog.class);
        org.mockito.Mockito.verify(botDetectionLogRepository).save(captor.capture());
        BotDetectionLog saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getCourseId()).isEqualTo(100L);
        assertThat(saved.getSuspicionScore()).isEqualTo(0.9);
        assertThat(saved.getActionTaken()).isEqualTo("FLAGGED");
        assertThat(saved.getRequestFeatures()).contains("ENROLLED");
    }

    @Test
    void processRecord_handlesMissingInterval_asFirstRequest() {
        EnrollEventConsumer consumer = new EnrollEventConsumer(redisTemplate, scorer, botDetectionLogRepository, new ObjectMapper());

        Map<String, String> fields = Map.of(
                "userId", "2",
                "courseId", "200",
                "outcome", "ENROLLED",
                "intervalSeconds", "",
                "repeatedCount1Min", "1",
                "userAgentSuspicious", "false"
        );
        when(scorer.score(any())).thenReturn(new BotDetectionScorer.ScoringResult(0.0, "LOGGED"));

        consumer.processRecord(record(fields));

        ArgumentCaptor<BotDetectionScorer.BotRequestFeatures> featuresCaptor =
                ArgumentCaptor.forClass(BotDetectionScorer.BotRequestFeatures.class);
        org.mockito.Mockito.verify(scorer).score(featuresCaptor.capture());
        assertThat(featuresCaptor.getValue().intervalSeconds()).isNull();
    }
}
