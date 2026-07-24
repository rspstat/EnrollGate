package com.enrollgate.enrollment.queue.websocket;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 과목별 대기열 WebSocket 세션을 (courseId, userId) 키로 보관한다. 단일 인스턴스 기준 구현 —
 * 여러 인스턴스로 수평 확장하려면 Redis Pub/Sub으로 순번 이벤트를 인스턴스 간에 동기화해야 한다
 * (Architecture 문서 Open Question, 2단계 이후 과제로 남겨둠).
 */
@Component
public class QueueSessionRegistry {

    private final Map<Long, Map<Long, WebSocketSession>> sessionsByCourse = new ConcurrentHashMap<>();

    public void register(Long courseId, Long userId, WebSocketSession session) {
        sessionsByCourse.computeIfAbsent(courseId, key -> new ConcurrentHashMap<>()).put(userId, session);
    }

    public void unregister(Long courseId, Long userId) {
        Map<Long, WebSocketSession> sessions = sessionsByCourse.get(courseId);
        if (sessions != null) {
            sessions.remove(userId);
        }
    }

    public Optional<WebSocketSession> find(Long courseId, Long userId) {
        Map<Long, WebSocketSession> sessions = sessionsByCourse.get(courseId);
        return sessions == null ? Optional.empty() : Optional.ofNullable(sessions.get(userId));
    }
}
