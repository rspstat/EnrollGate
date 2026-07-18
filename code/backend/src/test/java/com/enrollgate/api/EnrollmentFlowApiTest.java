package com.enrollgate.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enrollgate.course.controller.CreateCourseRequest;
import com.enrollgate.user.controller.LoginRequest;
import com.enrollgate.user.controller.SignupRequest;
import com.enrollgate.user.domain.User;
import com.enrollgate.user.domain.UserRole;
import com.enrollgate.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 회원가입 → 로그인 → 관리자 과목 등록 → 수강신청 → 대기열 → 확정 전체 플로우를 실제 HTTP 계층(MockMvc)으로 검증한다.
 * 유닛 테스트가 놓치기 쉬운 배선 문제(JWT 필터, 역할 기반 접근 제어, 예외 → 상태코드 매핑)를 잡기 위한 테스트.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnrollmentFlowApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void seedAdmin() throws Exception {
        if (!userRepository.existsByEmail("admin-flow@enrollgate.com")) {
            User admin = User.builder()
                    .email("admin-flow@enrollgate.com")
                    .passwordHash(passwordEncoder.encode("adminpass123"))
                    .name("관리자")
                    .studentNumber("ADMIN-FLOW-0001")
                    .role(UserRole.ADMIN)
                    .build();
            userRepository.save(admin);
        }
        adminToken = login("admin-flow@enrollgate.com", "adminpass123");
    }

    private String signupAndLogin(String email, String studentNumber) throws Exception {
        SignupRequest signup = new SignupRequest(email, "password123", "학생", studentNumber);
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());
        return login(email, "password123");
    }

    private String login(String email, String password) throws Exception {
        LoginRequest login = new LoginRequest(email, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createCourse(String token, int capacity) throws Exception {
        CreateCourseRequest request = new CreateCourseRequest(
                "CSE401", "데이터베이스시스템", "김OO", "CSE", 3, capacity, "2026-2",
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusHours(1));
        MvcResult result = mockMvc.perform(post("/api/v1/admin/courses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("courseId").asLong();
    }

    @Test
    void adminOnlyEndpoint_rejectsStudentToken() throws Exception {
        String studentToken = signupAndLogin("student-forbidden@enrollgate.com", "S-FORBIDDEN");
        CreateCourseRequest request = new CreateCourseRequest(
                "CSE401", "n", "p", "d", 3, 10, "2026-2", LocalDateTime.now(), LocalDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/v1/admin/courses")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void fullEnrollmentLifecycle_enrollListCancel_blocksReEnrollment() throws Exception {
        long courseId = createCourse(adminToken, 5);
        String studentToken = signupAndLogin("student-flow@enrollgate.com", "S-FLOW");

        mockMvc.perform(get("/api/v1/courses").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses").isArray());

        MvcResult enrollResult = mockMvc.perform(post("/api/v1/courses/" + courseId + "/enroll")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andReturn();
        long enrollmentId = objectMapper.readTree(enrollResult.getResponse().getContentAsString())
                .get("enrollmentId").asLong();

        mockMvc.perform(get("/api/v1/enrollments/me").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ENROLLED"));

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/enroll").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_ENROLLED"));

        mockMvc.perform(delete("/api/v1/enrollments/" + enrollmentId).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());

        // 재신청 미허용 정책(enrollments 유니크 제약과 일치): 취소 후에도 같은 과목 재신청은 차단된다
        mockMvc.perform(post("/api/v1/courses/" + courseId + "/enroll").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_ENROLLED"));
    }

    @Test
    void queueLifecycle_queuesThenPromotesAndConfirmsAfterSeatFrees() throws Exception {
        long courseId = createCourse(adminToken, 1);
        String firstToken = signupAndLogin("student-queue-1@enrollgate.com", "S-QUEUE-1");
        String secondToken = signupAndLogin("student-queue-2@enrollgate.com", "S-QUEUE-2");

        MvcResult firstEnroll = mockMvc.perform(post("/api/v1/courses/" + courseId + "/enroll")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isCreated())
                .andReturn();
        long firstEnrollmentId = objectMapper.readTree(firstEnroll.getResponse().getContentAsString())
                .get("enrollmentId").asLong();

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/enroll").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.queuePosition").value(1));

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/queue/status").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"));

        // 첫 번째 학생이 취소하면 cancel() 트랜잭션 내부에서 즉시 다음 순번이 NOTIFIED로 승격된다
        mockMvc.perform(delete("/api/v1/enrollments/" + firstEnrollmentId).header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/queue/status").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOTIFIED"));

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/queue/confirm").header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ENROLLED"));
    }
}
