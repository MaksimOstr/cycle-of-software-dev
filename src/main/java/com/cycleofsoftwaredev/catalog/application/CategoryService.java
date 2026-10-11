package com.cycleofsoftwaredev.catalog.application;

import com.cycleofsoftwaredev.catalog.domain.Category;
import com.cycleofsoftwaredev.catalog.domain.CategoryRepository;
import com.cycleofsoftwaredev.catalog.domain.ProductRepository;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Use cases "Category management" and "Category navigation". */
@Service
@Transactional
public class CategoryService {

    /** Requirement "Category navigation": the tree has up to 3 levels, for example Home > Kitchen > Cookware. */
    public static final int MAX_DEPTH = 3;

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    public Category createCategory(String name, UUID parentId) {
        if (parentId != null && depthOf(getCategory(parentId)) >= MAX_DEPTH) {
            throw new BusinessRuleException("The category tree can have at most " + MAX_DEPTH + " levels");
        }
        return categories.save(new Category(UUID.randomUUID(), name, parentId));
    }

    public Category renameCategory(UUID id, String newName) {
        Category category = getCategory(id);
        return categories.save(new Category(category.id(), newName, category.parentId()));
    }

    /** A category that contains products or subcategories cannot be deleted. */
    public void deleteCategory(UUID id) {
        getCategory(id);
        if (categories.existsByParentId(id)) {
            throw new BusinessRuleException("The category has subcategories and cannot be deleted");
        }
        if (products.existsByCategoryId(id)) {
            throw new BusinessRuleException("The category contains products and cannot be deleted");
        }
        categories.deleteById(id);
    }

    public List<Category> listCategories() {
        return categories.findAll().stream().sorted(Comparator.comparing(Category::name)).toList();
    }

    public Category getCategory(UUID id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
    }

    /** The category itself and all its subcategories: a category page shows the products of the whole branch. */
    public Set<UUID> branchIds(UUID rootId) {
        List<Category> all = categories.findAll();
        Set<UUID> branch = new HashSet<>(Set.of(getCategory(rootId).id()));
        boolean added = true;
        while (added) {
            added = false;
            for (Category category : all) {
                if (category.parentId() != null && branch.contains(category.parentId()) && branch.add(category.id())) {
                    added = true;
                }
            }
        }
        return branch;
    }

    private int depthOf(Category category) {
        int depth = 1;
        Category current = category;
        while (!current.isRoot()) {
            current = getCategory(current.parentId());
            depth++;
        }
        return depth;
    }
}
