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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public Page<ProductResponse> getAllProducts(String search, Pageable pageable) {
        log.info("Fetching products. Search={}, Page={}, Size={}", search, pageable.getPageNumber(), pageable.getPageSize());
        Page<ProductResponse> products = productRepository.searchProducts(search, pageable).map(this::mapToResponse);

        log.info("Retrieved {} products", products.getTotalElements());

        return products;
    }

    public ProductResponse getProductById(UUID id) {
        log.info("Fetching product. ProductId={}", id);

        Product product = productRepository.findByIdAndActiveTrue(id).orElseThrow(() -> {
            log.warn("Product not found. ProductId={}", id);
            return new ResourceNotFoundException("Product not found: " + id);
        });

        log.info("Product retrieved successfully. ProductId={}", id);

        return mapToResponse(product);
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {

        log.info("Product creation initiated. SKU={}, ProductName={}", request.getSku(), request.getName());

        if (productRepository.existsBySku(request.getSku())) {

            log.warn("Product creation failed. Duplicate SKU={}", request.getSku());

            throw new ConflictException("SKU already exists: " + request.getSku());
        }

        log.debug("Fetching category. CategoryId={}", request.getCategoryId());

        Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow(() -> {
            log.warn("Category not found. CategoryId={}", request.getCategoryId());
            return new ResourceNotFoundException("Category not found");
        });

        Product product = Product.builder().name(request.getName()).description(request.getDescription()).price(request.getPrice()).sku(request.getSku()).category(category).active(true).build();

        Product savedProduct = productRepository.save(product);

        log.info("Product created successfully. ProductId={}, SKU={}", savedProduct.getId(), savedProduct.getSku());

        return mapToResponse(savedProduct);
    }

    @Transactional
    public ProductResponse updateProduct(UUID id, UpdateProductRequest request) {

        log.info("Product update initiated. ProductId={}", id);

        Product product = productRepository.findByIdAndActiveTrue(id).orElseThrow(() -> {
            log.warn("Product not found. ProductId={}", id);
            return new ResourceNotFoundException("Product not found: " + id);
        });

        if (request.getName() != null) {
            product.setName(request.getName());
        }

        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }

        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }

        if (request.getCategoryId() != null) {

            log.debug("Updating category for ProductId={}, CategoryId={}", id, request.getCategoryId());

            Category category = categoryRepository.findById(request.getCategoryId()).orElseThrow(() -> {
                log.warn("Category not found. CategoryId={}", request.getCategoryId());
                return new ResourceNotFoundException("Category not found");
            });

            product.setCategory(category);
        }

        Product updatedProduct = productRepository.save(product);

        log.info("Product updated successfully. ProductId={}", updatedProduct.getId());

        return mapToResponse(updatedProduct);
    }

    @Transactional
    public void deleteProduct(UUID id) {

        log.info("Product deletion initiated. ProductId={}", id);

        Product product = productRepository.findByIdAndActiveTrue(id).orElseThrow(() -> {
            log.warn("Product not found. ProductId={}", id);
            return new ResourceNotFoundException("Product not found: " + id);
        });

        product.setActive(false);

        productRepository.save(product);

        log.info("Product soft deleted successfully. ProductId={}", id);
    }

    private ProductResponse mapToResponse(Product product) {

        CategoryResponse categoryResponse = null;

        if (product.getCategory() != null) {
            categoryResponse = CategoryResponse.builder().id(product.getCategory().getId()).name(product.getCategory().getName()).parentCategoryId(product.getCategory().getParentCategoryId()).build();
        }

        return ProductResponse.builder().id(product.getId()).name(product.getName()).description(product.getDescription()).price(product.getPrice()).sku(product.getSku()).category(categoryResponse).createdAt(product.getCreatedAt()).build();
    }
}