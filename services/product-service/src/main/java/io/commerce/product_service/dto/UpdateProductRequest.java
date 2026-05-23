package io.commerce.product_service.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class UpdateProductRequest {

    private String name;

    private String description;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price;

    private UUID categoryId;
}
