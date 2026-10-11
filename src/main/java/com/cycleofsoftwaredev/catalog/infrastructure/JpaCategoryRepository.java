package com.cycleofsoftwaredev.catalog.infrastructure;

import com.cycleofsoftwaredev.catalog.domain.Category;
import com.cycleofsoftwaredev.catalog.domain.CategoryRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** PostgreSQL storage of categories; the methods of {@link CategoryRepository} are implemented by Spring Data. */
public interface JpaCategoryRepository extends JpaRepository<Category, UUID>, CategoryRepository {
}
