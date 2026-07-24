package com.enrollgate.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enrollgate.common.security.JwtTokenProvider;
import com.enrollgate.user.domain.User;
import com.enrollgate.user.domain.UserRole;
import com.enrollgate.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private User persistedUser(Long id) {
        User user = User.builder()
                .email("student@enrollgate.com")
                .passwordHash("encoded-hash")
                .name("홍길동")
                .studentNumber("2024001")
                .role(UserRole.STUDENT)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void signup_succeeds_whenEmailAndStudentNumberAreUnique() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByStudentNumber(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });

        Long userId = authService.signup("student@enrollgate.com", "password123", "홍길동", "2024001");

        assertThat(userId).isEqualTo(1L);
        verify(userRepository).save(any(User.class));
    }

    @Test
    void signup_throws_whenEmailAlreadyExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.signup("student@enrollgate.com", "password123", "홍길동", "2024001"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void signup_throws_whenStudentNumberAlreadyExists() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByStudentNumber(anyString())).thenReturn(true);

        assertThatThrownBy(() -> authService.signup("student@enrollgate.com", "password123", "홍길동", "2024001"))
                .isInstanceOf(DuplicateStudentNumberException.class);
    }

    @Test
    void login_returnsAccessToken_whenCredentialsAreValid() {
        User user = persistedUser(1L);
        when(userRepository.findByEmail("student@enrollgate.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "encoded-hash")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(1L, "STUDENT")).thenReturn("jwt-token");

        String token = authService.login("student@enrollgate.com", "password123");

        assertThat(token).isEqualTo("jwt-token");
    }

    @Test
    void login_throws_whenUserNotFound() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("nobody@enrollgate.com", "password123"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_throws_whenPasswordDoesNotMatch() {
        User user = persistedUser(1L);
        when(userRepository.findByEmail("student@enrollgate.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "encoded-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("student@enrollgate.com", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
