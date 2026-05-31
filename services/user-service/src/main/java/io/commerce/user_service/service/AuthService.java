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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final PasswordResetService passwordResetService;
    private final Tracer tracer;

    public UserResponse register(RegisterRequest request) {

        log.info("Registration initiated for email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed. Email already exists: {}", request.getEmail());
            throw new ConflictException("Email already registered");
        }

        log.debug("Fetching default customer role");

        Role customerRole = roleRepository.findByName("ROLE_CUSTOMER")
                .orElseThrow(() -> {
                    log.error("Default role ROLE_CUSTOMER not found");
                    return new RuntimeException("Default role not found");
                });

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .active(true)
                .roles(Set.of(customerRole))
                .build();

        log.debug("Saving user to database");

        User saved = userRepository.save(user);

        log.info("User registered successfully. UserId={}, Email={}", saved.getId(), saved.getEmail());
        return mapToUserResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt received for email: {}", request.getEmail());

        User user = userRepository.findByEmailAndActiveTrue(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Login failed. User not found for email: {}", request.getEmail());
                    return new UnauthorizedException("Invalid credentials");
                });


        var span = tracer.currentSpan();

        if (span != null) {
            span.tag("userId", user.getId().toString());
            span.tag("userEmail", user.getEmail());

            log.debug("Trace information attached. TraceId={}", span.context().traceId());
        }

        log.debug("Validating password for UserId={}", user.getId());

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed. Invalid password for UserId={}", user.getId());
            throw new UnauthorizedException("Invalid credentials");
        }

        List<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        log.debug("Generating JWT token for UserId={}", user.getId());

        String token = jwtService.generateToken(
                user.getId().toString(),
                user.getEmail(),
                roles
        );

        log.debug("Creating refresh token for UserId={}", user.getId());

        RefreshToken refreshToken= refreshTokenService.createRefreshToken(user);

        log.info("User authenticated successfully. UserId={}, Email={}", user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .refreshToken(refreshToken.getToken())
                .expiresIn(3600000)
                .build();

    }


    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Refresh token request received");
        RefreshToken refreshToken = refreshTokenService.rotateRefreshToken(request.getRefreshToken());

        List<String> roles = refreshToken.getUser().getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        log.debug("Generating new access token for UserId={}", refreshToken.getUser().getId());

        String token = jwtService.generateToken(
                refreshToken.getUser().getId().toString(),
                refreshToken.getUser().getEmail(),
                roles
        );

        log.info("Refresh token rotated successfully for UserId={}", refreshToken.getUser().getId());

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .refreshToken(refreshToken.getToken())
                .expiresIn(3600000)
                .build();
    }

    public void forgotPassword(ForgotPasswordRequest request){
        log.info("Password reset OTP requested for email: {}", request.getEmail());
        otpService.sendOtp(request.getEmail());
        log.info("OTP sent successfully for email: {}", request.getEmail());
    }

    public Map<String, String> verifyOtp(VerifyOtpRequest request){
        log.info("OTP verification requested for email: {}", request.getEmail());

        String resetToken = otpService.verifyOtp(request.getEmail(), request.getOtp());
        Map<String, String> response = new HashMap<>();
        response.put("resetToken", resetToken);

        log.info("OTP verified successfully for email: {}", request.getEmail());
        return response;
    }

    public void  resetPassword(ResetPasswordRequest request){
        log.info("Password reset request received");
        passwordResetService.resetPassword(request.getResetToken(), request.getNewPassword());
        log.info("Password reset completed successfully");
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
