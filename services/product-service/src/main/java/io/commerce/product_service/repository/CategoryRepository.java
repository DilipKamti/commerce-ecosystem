package io.commerce.product_service.repository;

import io.commerce.product_service.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findAllByActiveTrue();
    boolean existsByName(String name);
}
