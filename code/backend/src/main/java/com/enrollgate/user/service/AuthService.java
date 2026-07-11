package com.enrollgate.user.service;

import com.enrollgate.common.security.JwtTokenProvider;
import com.enrollgate.user.domain.User;
import com.enrollgate.user.domain.UserRole;
import com.enrollgate.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public Long signup(String email, String rawPassword, String name, String studentNumber) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }
        if (userRepository.existsByStudentNumber(studentNumber)) {
            throw new DuplicateStudentNumberException(studentNumber);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .name(name)
                .studentNumber(studentNumber)
                .role(UserRole.STUDENT)
                .build();

        return userRepository.save(user).getId();
    }

    @Transactional(readOnly = true)
    public String login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return jwtTokenProvider.createAccessToken(user.getId(), user.getRole());
    }
}
