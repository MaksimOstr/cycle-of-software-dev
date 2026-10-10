package com.cycleofsoftwaredev.catalog.application;

import com.cycleofsoftwaredev.catalog.domain.Category;
import com.cycleofsoftwaredev.catalog.domain.CategoryRepository;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Use cases "Category management" and "Category navigation". */
@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    public Category createCategory(String name, UUID parentId) {
        if (parentId != null) {
            getCategory(parentId);
        }
        return categories.save(new Category(UUID.randomUUID(), name, parentId));
    }

    public List<Category> listCategories() {
        return categories.findAll().stream().sorted(Comparator.comparing(Category::name)).toList();
    }

    public Category getCategory(UUID id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
    }
}
