package io.commerce.product_service.service;

import io.commerce.product_service.dto.CategoryResponse;
import io.commerce.product_service.dto.CreateCategoryRequest;
import io.commerce.product_service.entity.Category;
import io.commerce.product_service.exception.ConflictException;
import io.commerce.product_service.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getAllCategories() {
        log.info("Fetching all active categories");
        List<CategoryResponse> categories = categoryRepository.findAllByActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        log.info("Retrieved {} active categories", categories.size());

        return categories;
    }

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        log.info("Category creation initiated. CategoryName={}", request.getName());

        if (categoryRepository.existsByName(request.getName())) {

            log.warn("Category creation failed. Category already exists. CategoryName={}",request.getName());

            throw new ConflictException("Category already exists: " + request.getName());
        }

        log.debug("Creating category entity. CategoryName={}", request.getName());

        Category category = Category.builder()
                .name(request.getName())
                .parentCategoryId(request.getParentCategoryId())
                .active(true)
                .build();

        Category savedCategory = categoryRepository.save(category);

        log.info("Category created successfully. CategoryId={}, CategoryName={}", savedCategory.getId(), savedCategory.getName());

        return mapToResponse(savedCategory);
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .parentCategoryId(category.getParentCategoryId())
                .build();
    }
}
