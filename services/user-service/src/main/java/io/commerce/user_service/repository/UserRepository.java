package io.commerce.user_service.repository;

import io.commerce.user_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailAndActiveTrue(String email);
    boolean existsByEmail(String email);
}
