package io.commerce.product_service.controller;

import io.commerce.product_service.dto.CreateProductRequest;
import io.commerce.product_service.dto.ProductResponse;
import io.commerce.product_service.dto.UpdateProductRequest;
import io.commerce.product_service.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllProducts(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {

        String[] sortParams = sort.split(",");
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.fromString(sortParams[1]), sortParams[0]));

        Page<ProductResponse> products = productService.getAllProducts(search, pageable);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", products.getContent(),
                "meta", Map.of(
                        "page", products.getNumber(),
                        "size", products.getSize(),
                        "totalElements", products.getTotalElements(),
                        "totalPages", products.getTotalPages()
                )
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProductById(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("success", true, "data", productService.getProductById(id)));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createProduct(
            @Valid @RequestBody CreateProductRequest request) {
        ProductResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("success", true, "data", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(Map.of("success", true, "data",
                productService.updateProduct(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Product deleted"));
    }
}
