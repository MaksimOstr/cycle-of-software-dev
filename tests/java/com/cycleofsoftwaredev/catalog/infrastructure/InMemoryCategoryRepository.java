package com.cycleofsoftwaredev.catalog.infrastructure;

import com.cycleofsoftwaredev.catalog.domain.Category;
import com.cycleofsoftwaredev.catalog.domain.CategoryRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double: lets the unit tests of the services run without a database. */
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

    @Override
    public boolean existsByParentId(UUID parentId) {
        return categories.values().stream().anyMatch(category -> parentId.equals(category.parentId()));
    }

    @Override
    public void deleteById(UUID id) {
        categories.remove(id);
    }
}
