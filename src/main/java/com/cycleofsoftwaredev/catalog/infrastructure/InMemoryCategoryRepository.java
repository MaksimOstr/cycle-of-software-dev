package com.cycleofsoftwaredev.catalog.infrastructure;

import com.cycleofsoftwaredev.catalog.domain.Category;
import com.cycleofsoftwaredev.catalog.domain.CategoryRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/** In-memory storage used until the PostgreSQL schema of the module is created (work item SHOP-16). */
@Repository
public class InMemoryCategoryRepository implements CategoryRepository {

    private final Map<UUID, Category> categories = new ConcurrentHashMap<>();

    @Override
    public Category save(Category category) {
        categories.put(category.id(), category);
        return category;
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return Optional.ofNullable(categories.get(id));
    }

    @Override
    public List<Category> findAll() {
        return List.copyOf(categories.values());
    }
}
