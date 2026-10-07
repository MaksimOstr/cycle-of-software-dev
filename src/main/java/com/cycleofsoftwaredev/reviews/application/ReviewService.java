package com.cycleofsoftwaredev.reviews.application;

import com.cycleofsoftwaredev.ordering.api.PurchaseVerificationApi;
import com.cycleofsoftwaredev.reviews.domain.Review;
import com.cycleofsoftwaredev.reviews.domain.ReviewRepository;
import com.cycleofsoftwaredev.reviews.domain.ReviewStatus;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Use cases "Write a review" and "Review moderation". */
@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final PurchaseVerificationApi purchases;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews, PurchaseVerificationApi purchases, Clock clock) {
        this.reviews = reviews;
        this.purchases = purchases;
        this.clock = clock;
    }

    public Review writeReview(UUID productId, UUID customerId, int rating, String text) {
        if (!purchases.hasReceivedProduct(customerId, productId)) {
            throw new BusinessRuleException("Only customers who received the product can review it");
        }
        boolean alreadyReviewed = reviews.findByProductId(productId).stream()
                .anyMatch(review -> review.customerId().equals(customerId));
        if (alreadyReviewed) {
            throw new BusinessRuleException("You have already reviewed this product");
        }
        return reviews.save(new Review(UUID.randomUUID(), productId, customerId, rating, text, clock.instant()));
    }

    public Review moderate(UUID reviewId, boolean approved) {
        Review review = reviews.findById(reviewId).orElseThrow(() -> new NotFoundException("Review", reviewId));
        if (approved) {
            review.approve();
        } else {
            review.reject();
        }
        return reviews.save(review);
    }

    public List<Review> approvedReviews(UUID productId) {
        return reviews.findByProductId(productId).stream()
                .filter(review -> review.status() == ReviewStatus.APPROVED)
                .sorted(Comparator.comparing(Review::createdAt).reversed())
                .toList();
    }

    public OptionalDouble averageRating(UUID productId) {
        return approvedReviews(productId).stream().mapToInt(Review::rating).average();
    }
}
