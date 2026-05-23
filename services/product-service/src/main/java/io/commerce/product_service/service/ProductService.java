package io.commerce.product_service.service;

import io.commerce.product_service.dto.CategoryResponse;
import io.commerce.product_service.dto.CreateProductRequest;
import io.commerce.product_service.dto.ProductResponse;
import io.commerce.product_service.dto.UpdateProductRequest;
import io.commerce.product_service.entity.Category;
import io.commerce.product_service.entity.Product;
import io.commerce.product_service.exception.ConflictException;
import io.commerce.product_service.exception.ResourceNotFoundException;
import io.commerce.product_service.repository.CategoryRepository;
import io.commerce.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public Page<ProductResponse> getAllProducts(String search, Pageable pageable) {
        return productRepository.searchProducts(search, pageable)
                .map(this::mapToResponse);
    }

    public ProductResponse getProductById(UUID id) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new ConflictException("SKU already exists: " + request.getSku());
        }
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .sku(request.getSku())
                .category(category)
                .active(true)
                .build();

        return mapToResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));

        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            product.setCategory(category);
        }

        return mapToResponse(productRepository.save(product));
    }

    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + id));
        product.setActive(false);  // soft delete
        productRepository.save(product);
    }

    private ProductResponse mapToResponse(Product product) {
        CategoryResponse categoryResponse = null;
        if (product.getCategory() != null) {
            categoryResponse = CategoryResponse.builder()
                    .id(product.getCategory().getId())
                    .name(product.getCategory().getName())
                    .parentCategoryId(product.getCategory().getParentCategoryId())
                    .build();
        }
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .sku(product.getSku())
                .category(categoryResponse)
                .createdAt(product.getCreatedAt())
                .build();
    }
}
