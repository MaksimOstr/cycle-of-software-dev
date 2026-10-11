package com.cycleofsoftwaredev.reviews.infrastructure;

import com.cycleofsoftwaredev.reviews.domain.Review;
import com.cycleofsoftwaredev.reviews.domain.ReviewRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory test double: lets the unit tests of the services run without a database. */
public class InMemoryReviewRepository implements ReviewRepository {

    private final Map<UUID, Review> reviews = new ConcurrentHashMap<>();

    @Override
    public Review save(Review review) {
        reviews.put(review.id(), review);
        return review;
    }

    @Override
    public Optional<Review> findById(UUID id) {
        return Optional.ofNullable(reviews.get(id));
    }

    @Override
    public List<Review> findByProductId(UUID productId) {
        return reviews.values().stream().filter(review -> review.productId().equals(productId)).toList();
    }
}
