package com.enrollgate.enrollment.queue.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

/**
 * ERD-API-Spec 3.4의 WebSocket 메시지 포맷(POSITION_UPDATE / YOUR_TURN / EXPIRED)으로 push한다.
 * 연결이 없거나 끊긴 사용자는 조용히 무시한다 — 폴링 API(GET /queue/status)가 항상 폴백으로 존재하므로
 * WebSocket push 실패가 기능 자체를 막지는 않는다(best-effort).
 */
@Component
@RequiredArgsConstructor
public class QueueNotificationService {

    private final QueueSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public void notifyPositionUpdate(Long courseId, Long userId, long position, long estimatedWaitSeconds) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "POSITION_UPDATE");
        message.put("position", position);
        message.put("estimatedWaitSeconds", estimatedWaitSeconds);
        send(courseId, userId, message);
    }

    public void notifyYourTurn(Long courseId, Long userId, LocalDateTime confirmDeadline) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("type", "YOUR_TURN");
        message.put("confirmDeadline", confirmDeadline);
        message.put("confirmUrl", "/api/v1/courses/" + courseId + "/queue/confirm");
        send(courseId, userId, message);
    }

    public void notifyExpired(Long courseId, Long userId) {
        send(courseId, userId, Map.of("type", "EXPIRED"));
    }

    private void send(Long courseId, Long userId, Object payload) {
        sessionRegistry.find(courseId, userId).ifPresent(session -> {
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
                }
            } catch (IOException ignored) {
                // best-effort push: 실패해도 폴링 API로 대체 가능하므로 예외를 전파하지 않는다.
            }
        });
    }
}
