package io.commerce.user_service.service;

import io.commerce.user_service.entity.RefreshToken;
import io.commerce.user_service.entity.User;
import io.commerce.user_service.exception.BadRequestException;
import io.commerce.user_service.repository.RefreshTokenRepository;
import io.commerce.user_service.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public void resetPassword(String resetToken, String newPassword) {

        log.info("Password reset process initiated");

        RefreshToken refreshToken = refreshTokenRepository.findByToken(resetToken)
                .orElseThrow(() -> {
                    log.warn("Password reset failed. Invalid reset token");
                    return new BadRequestException("Invalid reset token");
                });

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Password reset failed. Expired reset token for UserId={}", refreshToken.getUser().getId());
            throw new BadRequestException("Reset token expired");
        }

        User user = refreshToken.getUser();

        log.debug("Updating password for UserId={}", user.getId());

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        log.debug("Removing reset token for UserId={}", user.getId());

        refreshTokenRepository.deleteByToken(resetToken);

        log.debug("Removing all active refresh tokens for UserId={}", user.getId());

        refreshTokenRepository.deleteByUser(user);

        log.info("Password reset completed successfully for UserId={}", user.getId());
    }
}