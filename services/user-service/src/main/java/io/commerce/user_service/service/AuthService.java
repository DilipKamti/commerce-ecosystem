package io.commerce.user_service.service;

import io.commerce.user_service.dto.*;
import io.commerce.user_service.entity.RefreshToken;
import io.commerce.user_service.entity.Role;
import io.commerce.user_service.entity.User;
import io.commerce.user_service.exception.ConflictException;
import io.commerce.user_service.exception.UnauthorizedException;
import io.commerce.user_service.repository.RoleRepository;
import io.commerce.user_service.repository.UserRepository;
import io.commerce.user_service.security.JwtService;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final Tracer tracer;

    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered");
        }

        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .active(true)
                .roles(Set.of(customerRole))
                .build();

        User saved = userRepository.save(user);
        return mapToUserResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndActiveTrue(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        // Get current span and add tags
        var span = tracer.currentSpan();
        if (span != null) {
            span.tag("userId", user.getId().toString());
            span.tag("userEmail", user.getEmail());
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        String token = jwtService.generateToken(
                user.getId().toString(),
                user.getEmail(),
                roles
        );

       RefreshToken refreshToken= refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .refreshToken(refreshToken.getToken())
                .expiresIn(3600000)
                .build();

    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenService.rotateRefreshToken(request.getRefreshToken());

        List<String> roles = refreshToken.getUser().getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        String token = jwtService.generateToken(
                refreshToken.getUser().getId().toString(),
                refreshToken.getUser().getEmail(),
                roles
        );

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .refreshToken(refreshToken.getToken())
                .expiresIn(3600000)
                .build();
    }


    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
