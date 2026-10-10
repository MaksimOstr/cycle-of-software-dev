package com.cycleofsoftwaredev.catalog.domain;

import java.util.Objects;
import java.util.UUID;

/** Node of the category tree. A root category has no parent. */
public record Category(UUID id, String name, UUID parentId) {

    public Category {
        Objects.requireNonNull(id);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        name = name.trim();
    }

    public boolean isRoot() {
        return parentId == null;
    }
}
