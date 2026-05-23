package io.commerce.product_service.service;

import io.commerce.product_service.dto.CategoryResponse;
import io.commerce.product_service.dto.CreateCategoryRequest;
import io.commerce.product_service.entity.Category;
import io.commerce.product_service.exception.ConflictException;
import io.commerce.product_service.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllByActiveTrue()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.existsByName(request.getName())) {
            throw new ConflictException("Category already exists: " + request.getName());
        }
        Category category = Category.builder()
                .name(request.getName())
                .parentCategoryId(request.getParentCategoryId())
                .active(true)
                .build();
        return mapToResponse(categoryRepository.save(category));
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .parentCategoryId(category.getParentCategoryId())
                .build();
    }
}
