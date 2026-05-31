package io.commerce.user_service.service;

import io.commerce.user_service.entity.RefreshToken;
import io.commerce.user_service.entity.User;
import io.commerce.user_service.exception.UnauthorizedException;
import io.commerce.user_service.repository.RefreshTokenRepository;
import io.commerce.user_service.repository.UserRepository;
import io.commerce.user_service.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository ;
    private final JwtService jwtService ;

    public RefreshToken createRefreshToken(User user){
        log.debug("Creating refresh token for UserId={}", user.getId());

        refreshTokenRepository.deleteByUser(user);
        RefreshToken refreshToken = refreshTokenRepository.save(
                RefreshToken.builder()
                        .token(jwtService.generateRefreshToken())
                        .expiresAt(jwtService.getRefreshTokenExpiry())
                        .user(user)
                        .build()
        );

        log.info("Refresh token created successfully for UserId={}", user.getId());

        return refreshToken;
    }

    public RefreshToken rotateRefreshToken(String token) {

        log.info("Refresh token rotation initiated");

        RefreshToken existingToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> {
                    log.warn("Refresh token rotation failed. Invalid token");
                    return new UnauthorizedException("Invalid refresh token");
                });

        if (existingToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Refresh token rotation failed. Token expired for UserId={}", existingToken.getUser().getId());
            throw new UnauthorizedException("Refresh token expired");
        }

        log.debug("Deleting existing refresh token for UserId={}", existingToken.getUser().getId());

        refreshTokenRepository.deleteByToken(token);

        RefreshToken newToken = createRefreshToken(existingToken.getUser());

        log.info("Refresh token rotated successfully for UserId={}", existingToken.getUser().getId());

        return newToken;
    }
}
