package io.commerce.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductResponse {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal price;
    private String sku;
    private CategoryResponse category;
    private LocalDateTime createdAt;
}
