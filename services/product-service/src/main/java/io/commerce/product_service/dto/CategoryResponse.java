package io.commerce.product_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CategoryResponse {
    private UUID id;
    private String name;
    private UUID parentCategoryId;
}
