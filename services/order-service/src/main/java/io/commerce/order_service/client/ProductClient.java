package io.commerce.order_service.client;

import io.commerce.order_service.dto.ProductApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "product-service", fallback = ProductClientFallback.class)
public interface ProductClient {
    @GetMapping("/api/v1/products/{id}")
    ProductApiResponse getProductById(@PathVariable UUID id);
}
