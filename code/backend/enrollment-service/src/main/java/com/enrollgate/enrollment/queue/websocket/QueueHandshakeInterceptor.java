package com.enrollgate.enrollment.queue.websocket;

import com.enrollgate.common.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WS 핸드셰이크 단계에서 JWT를 검증하고 URL 경로변수(courseId)를 세션 attributes로 옮겨 담는다.
 * 브라우저 네이티브 WebSocket API는 커스텀 헤더를 지원하지 않으므로, 토큰은 쿼리 파라미터(?token=)로 받는다.
 */
@Component
@RequiredArgsConstructor
public class QueueHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        Long courseId = extractCourseId(request);
        if (courseId == null) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }

        String token = extractToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Claims claims = jwtTokenProvider.parseClaims(token);
            attributes.put("userId", Long.valueOf(claims.getSubject()));
            attributes.put("courseId", courseId);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    private Long extractCourseId(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }
        HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
        Object uriVariables = httpServletRequest.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(uriVariables instanceof Map<?, ?> variables)) {
            return null;
        }
        Object courseId = variables.get("courseId");
        try {
            return courseId == null ? null : Long.valueOf(courseId.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query == null) {
            return null;
        }
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                try {
                    return URLDecoder.decode(param.substring("token=".length()), StandardCharsets.UTF_8.name());
                } catch (UnsupportedEncodingException ex) {
                    return null;
                }
            }
        }
        return null;
    }
}
