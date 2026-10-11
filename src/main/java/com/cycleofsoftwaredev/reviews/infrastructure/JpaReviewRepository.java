package com.cycleofsoftwaredev.reviews.infrastructure;

import com.cycleofsoftwaredev.reviews.domain.Review;
import com.cycleofsoftwaredev.reviews.domain.ReviewRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** PostgreSQL storage of reviews. */
public interface JpaReviewRepository extends JpaRepository<Review, UUID>, ReviewRepository {
}
