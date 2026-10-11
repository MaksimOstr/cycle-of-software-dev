package com.cycleofsoftwaredev.catalog.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/** Node of the category tree. A root category has no parent. */
@Entity
@Table(name = "categories", schema = "catalog")
public class Category {

    @Id
    private UUID id;
    private String name;
    private UUID parentId;

    protected Category() {
        // for JPA
    }

    public Category(UUID id, String name, UUID parentId) {
        this.id = Objects.requireNonNull(id);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        this.name = name.trim();
        this.parentId = parentId;
    }

    public boolean isRoot() {
        return parentId == null;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID parentId() {
        return parentId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Category category && id.equals(category.id) && name.equals(category.name)
                && Objects.equals(parentId, category.parentId);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Category[id=" + id + ", name=" + name + ", parentId=" + parentId + "]";
    }
}
