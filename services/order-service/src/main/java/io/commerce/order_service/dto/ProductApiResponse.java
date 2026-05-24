package io.commerce.order_service.dto;

import lombok.Data;

@Data
public class ProductApiResponse {
    private boolean success;
    private ProductResponse data;
}