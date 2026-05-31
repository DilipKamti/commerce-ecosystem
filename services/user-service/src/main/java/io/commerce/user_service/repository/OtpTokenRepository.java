package io.commerce.user_service.repository;

import io.commerce.user_service.entity.OtpToken;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpTokenRepository extends JpaRepository<OtpToken, UUID> {

    Optional<OtpToken> findByEmailAndUsedFalse(String email);
    Optional<OtpToken> findByOtpAndEmailAndUsedFalse(String otp, String email);
    void deleteByEmail(String email);

    @Modifying
    @Transactional
    @Query("UPDATE OtpToken o SET o.used = true WHERE o.id = :id")
    void markAsUsed(@Param("id") UUID id);

    List<OtpToken> findAllByExpiresAtBefore(LocalDateTime dateTime);

    @Modifying
    @Transactional
    @Query("DELETE FROM OtpToken o WHERE o.expiresAt < :dateTime")
    void deleteAllExpiredOtps(@Param("dateTime") LocalDateTime dateTime);
}
