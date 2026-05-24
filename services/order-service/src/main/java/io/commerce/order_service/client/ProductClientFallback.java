package io.commerce.order_service.client;

import io.commerce.order_service.dto.ProductApiResponse;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProductClientFallback implements ProductClient {
    @Override
    public ProductApiResponse getProductById(UUID id) {
        // Circuit breaker open — return null, let service layer handle it
        return null;
    }
}
