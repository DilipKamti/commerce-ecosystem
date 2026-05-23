package io.commerce.product_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCategoryRequest {

    @NotBlank
    private String name;

    private UUID parentCategoryId;
}
