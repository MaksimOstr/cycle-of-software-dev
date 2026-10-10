package com.cycleofsoftwaredev.reviews.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository {

    Review save(Review review);

    Optional<Review> findById(UUID id);

    List<Review> findByProductId(UUID productId);
}
