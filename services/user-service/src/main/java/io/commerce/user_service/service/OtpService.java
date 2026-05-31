package io.commerce.user_service.service;

import io.commerce.user_service.entity.OtpToken;
import io.commerce.user_service.entity.RefreshToken;
import io.commerce.user_service.entity.User;
import io.commerce.user_service.exception.BadRequestException;
import io.commerce.user_service.exception.ResourceNotFoundException;
import io.commerce.user_service.repository.OtpTokenRepository;
import io.commerce.user_service.repository.RefreshTokenRepository;
import io.commerce.user_service.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OtpService {

    private final OtpTokenRepository otpTokenRepository;
    private final JavaMailSender javaMailSender;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public String generateOtp(){
        log.debug("Generating OTP");
        return String.valueOf(new Random().nextInt(900000) + 100000);
    }

    public void sendOtp(String email){
        log.info("OTP generation initiated for email: {}", email);
        if (!userRepository.existsByEmail(email)) {
            log.warn("OTP generation failed. Email not found: {}", email);
            throw new ResourceNotFoundException("Email not found");
        }
        log.debug("Deleting existing OTP records for email: {}", email);
        otpTokenRepository.deleteByEmail(email);

        String otp=generateOtp();
        OtpToken otpToken = OtpToken.builder()
               .otp(otp)
               .used(false)
               .email(email)
               .expiresAt(LocalDateTime.now().plusMinutes(10))
               .build();

        log.debug("Persisting OTP token for email: {}", email);
        otpTokenRepository.save(otpToken);

        log.debug("Sending OTP email to email: {}", email);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Your Commerce OTP Code");
        message.setText("Your OTP is: " + otp + "\nValid for 10 minutes.");
        javaMailSender.send(message);

        log.info("OTP email sent successfully to email: {}", email);

    }

    public String verifyOtp(String email, String otp) {
        log.info("OTP verification initiated for email: {}", email);
        OtpToken otpToken = otpTokenRepository
                .findByOtpAndEmailAndUsedFalse(otp, email)
                .orElseThrow(() -> {
                    log.warn("OTP verification failed. Invalid OTP for email: {}", email);
                    return new BadRequestException("Invalid OTP!");
                });

        if (otpToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("OTP verification failed. OTP expired for email: {}", email);
            throw new BadRequestException("OTP expired");
        }

        log.debug("Marking OTP as used for email: {}", email);
        otpTokenRepository.markAsUsed(otpToken.getId());

        User user = userRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> {
                    log.warn("OTP verified but user not found for email: {}", email);
                    return new ResourceNotFoundException("User not found");
                });

        log.debug("Generating password reset token for UserId={}", user.getId());

        RefreshToken resetToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        RefreshToken saved = refreshTokenRepository.save(resetToken);

        log.info("OTP verified successfully. Password reset token generated for UserId={}", user.getId());

        return saved.getToken();
    }

    @Scheduled(fixedRate = 300000)
    @Transactional
    public void cleanupExpiredOtps() {
        log.info("Scheduled OTP cleanup started");
        LocalDateTime now = LocalDateTime.now();

        List<OtpToken> expiredOtps = otpTokenRepository.findAllByExpiresAtBefore(now);
        int count = expiredOtps.size();

        if (count > 0) {
            expiredOtps.forEach(otp ->
                    log.debug("Deleting expired OTP for email: {}, expired at: {}",
                            otp.getEmail(), otp.getExpiresAt()));

            otpTokenRepository.deleteAllExpiredOtps(now);
            log.info("Scheduled OTP cleanup completed — deleted {} expired OTPs", count);
        } else {
            log.info("Scheduled OTP cleanup completed — no expired OTPs found");
        }
    }
}
