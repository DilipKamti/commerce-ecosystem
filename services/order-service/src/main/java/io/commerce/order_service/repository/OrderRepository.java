package io.commerce.order_service.repository;

import io.commerce.order_service.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByIdAndActiveTrue(UUID id);
    Page<Order> findAllByUserIdAndActiveTrue(UUID userId, Pageable pageable);
}
