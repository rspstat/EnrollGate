package com.enrollgate.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.enrollgate.course.controller.CreateCourseRequest;
import com.enrollgate.user.controller.LoginRequest;
import com.enrollgate.user.controller.SignupRequest;
import com.enrollgate.user.domain.User;
import com.enrollgate.user.domain.UserRole;
import com.enrollgate.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 실제 임베디드 서버(RANDOM_PORT)에 진짜 WebSocket 연결을 맺어 대기열 순번 push를 검증한다.
 * MockMvc는 실제 소켓을 열지 않으므로 이 흐름은 MockMvc로 검증할 수 없어 별도 테스트로 분리했다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueWebSocketFlowTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void seedAdmin() {
        if (!userRepository.existsByEmail("admin-ws@enrollgate.com")) {
            User admin = User.builder()
                    .email("admin-ws@enrollgate.com")
                    .passwordHash(passwordEncoder.encode("adminpass123"))
                    .name("관리자")
                    .studentNumber("ADMIN-WS-0001")
                    .role(UserRole.ADMIN)
                    .build();
            userRepository.save(admin);
        }
        adminToken = login("admin-ws@enrollgate.com", "adminpass123");
    }

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String login(String email, String password) {
        var response = restTemplate.postForEntity(
                baseUrl() + "/auth/login", new HttpEntity<>(new LoginRequest(email, password), jsonHeaders(null)),
                String.class);
        try {
            return objectMapper.readTree(response.getBody()).get("accessToken").asText();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String signupAndLogin(String email, String studentNumber) {
        var response = restTemplate.postForEntity(
                baseUrl() + "/auth/signup",
                new HttpEntity<>(new SignupRequest(email, "password123", "학생", studentNumber), jsonHeaders(null)),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return login(email, "password123");
    }

    private long createCourse(int capacity) throws Exception {
        CreateCourseRequest request = new CreateCourseRequest(
                "WS401", "WebSocket 테스트 과목", "김OO", "CSE", 3, capacity, "2026-2",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
        var response = restTemplate.postForEntity(
                baseUrl() + "/admin/courses", new HttpEntity<>(request, jsonHeaders(adminToken)), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return objectMapper.readTree(response.getBody()).get("courseId").asLong();
    }

    private static class RecordingHandler extends TextWebSocketHandler {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            messages.add(message.getPayload());
        }
    }

    @Test
    void secondStudentReceivesYourTurn_whenFirstStudentCancels() throws Exception {
        long courseId = createCourse(1);
        String firstToken = signupAndLogin("ws-student-1@enrollgate.com", "WS-STUDENT-1");
        String secondToken = signupAndLogin("ws-student-2@enrollgate.com", "WS-STUDENT-2");

        // 1번 학생이 신청해 정원을 채운다
        var firstEnroll = restTemplate.postForEntity(
                baseUrl() + "/courses/" + courseId + "/enroll", new HttpEntity<>(jsonHeaders(firstToken)), String.class);
        assertThat(firstEnroll.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long firstEnrollmentId = objectMapper.readTree(firstEnroll.getBody()).get("enrollmentId").asLong();

        // 2번 학생은 대기열로 들어간다
        var secondEnroll = restTemplate.postForEntity(
                baseUrl() + "/courses/" + courseId + "/enroll", new HttpEntity<>(jsonHeaders(secondToken)), String.class);
        assertThat(secondEnroll.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        // 2번 학생이 대기열 WebSocket에 연결
        StandardWebSocketClient client = new StandardWebSocketClient();
        RecordingHandler handler = new RecordingHandler();
        String wsUrl = "ws://localhost:" + port + "/ws/queue/" + courseId + "?token=" + secondToken;
        WebSocketSession session = client.execute(handler, new WebSocketHttpHeaders(), URI.create(wsUrl)).get(5, TimeUnit.SECONDS);
        assertThat(session.isOpen()).isTrue();

        try {
            // 1번 학생이 취소하면 cancel() 트랜잭션 안에서 즉시 2번 학생이 승격되고 WS로 YOUR_TURN이 push된다
            restTemplate.exchange(baseUrl() + "/enrollments/" + firstEnrollmentId,
                    org.springframework.http.HttpMethod.DELETE, new HttpEntity<>(jsonHeaders(firstToken)), Void.class);

            String message = handler.messages.poll(5, TimeUnit.SECONDS);
            assertThat(message).isNotNull();
            assertThat(message).contains("\"type\":\"YOUR_TURN\"");
            assertThat(message).contains("confirmUrl");
        } finally {
            session.close(CloseStatus.NORMAL);
        }
    }
}
