package io.commerce.user_service.service;

import io.commerce.user_service.entity.RefreshToken;
import io.commerce.user_service.entity.User;
import io.commerce.user_service.exception.UnauthorizedException;
import io.commerce.user_service.repository.RefreshTokenRepository;
import io.commerce.user_service.repository.UserRepository;
import io.commerce.user_service.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository ;
    private final JwtService jwtService ;

    public RefreshToken createRefreshToken(User user){
        refreshTokenRepository.deleteByUser(user);
        return  refreshTokenRepository.save(RefreshToken.builder()
                        .token(jwtService.generateRefreshToken())
                                .expiresAt(jwtService.getRefreshTokenExpiry())
                .user(user)
                .build());
    }

    public RefreshToken rotateRefreshToken(String token){
       Optional<RefreshToken> existingToken= refreshTokenRepository.findByToken(token);
        if(existingToken.isEmpty()){
            throw  new UnauthorizedException("Invalid refresh token");
        }
        if(existingToken.get().getExpiresAt().isBefore(LocalDateTime.now())){
            throw new UnauthorizedException("Refresh token expired");
        }

        refreshTokenRepository.deleteByToken(token);

        return createRefreshToken(existingToken.get().getUser());

    }
}
