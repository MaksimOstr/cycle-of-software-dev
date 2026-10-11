package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.catalog.domain.Category;
import java.util.UUID;

/** Category as returned by the catalog and management endpoints. */
public record CategoryResponse(UUID id, String name, UUID parentId) {

    static CategoryResponse of(Category category) {
        return new CategoryResponse(category.id(), category.name(), category.parentId());
    }
}
