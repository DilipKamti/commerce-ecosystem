package io.commerce.user_service.controller;
import io.commerce.user_service.dto.*;
import io.commerce.user_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request received for email: {}", request.getEmail());

        UserResponse user = authService.register(request);

        log.info("User registered successfully. UserId: {}", user.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "data", user
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received for email: {}", request.getEmail());

        AuthResponse response = authService.login(request);

        log.info("User logged in successfully: {}", request.getEmail());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", response
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(@Valid @RequestBody RefreshTokenRequest request){
        log.info("Refresh token request received");

        AuthResponse refreshToken = authService.refreshToken(request);

        log.info("Access token refreshed successfully");
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", refreshToken
        ));
    }

    @PostMapping("/forget-password")
    public ResponseEntity<Map<String, Object>> forgetPassword(@Valid @RequestBody ForgotPasswordRequest request){
        log.info("Forgot password request received for email: {}", request.getEmail());

        authService.forgotPassword(request);

        log.info("Password reset OTP sent successfully to email: {}", request.getEmail());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", "OTP sent to your email"
        ));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, Object>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request){
        log.info("OTP verification request received for email: {}", request.getEmail());

        Map<String, String> data = authService.verifyOtp(request);

        log.info("OTP verified successfully for email: {}", request.getEmail());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data
        ));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@Valid @RequestBody ResetPasswordRequest request){
        log.info("Password reset request received");

        authService.resetPassword(request);

        log.info("Password reset completed successfully");
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", "Password reset successfully"
        ));
    }
}
