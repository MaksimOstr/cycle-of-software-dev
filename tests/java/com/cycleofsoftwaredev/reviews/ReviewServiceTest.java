package com.cycleofsoftwaredev.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.ordering.api.PurchaseVerificationApi;
import com.cycleofsoftwaredev.reviews.application.ReviewService;
import com.cycleofsoftwaredev.reviews.domain.Review;
import com.cycleofsoftwaredev.reviews.domain.ReviewStatus;
import com.cycleofsoftwaredev.reviews.infrastructure.InMemoryReviewRepository;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.support.MutableClock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewServiceTest {

    private final UUID buyer = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    // the Reviews module needs only this narrow interface of Ordering, so a lambda is enough for the test
    private final PurchaseVerificationApi purchases = (customerId, product) -> customerId.equals(buyer);
    private final ReviewService reviews = new ReviewService(new InMemoryReviewRepository(), purchases,
            new MutableClock(Instant.parse("2026-10-05T10:00:00Z")));

    @Test
    void reviewIsVisibleOnlyAfterApproval() {
        Review review = reviews.writeReview(productId, buyer, 5, "Great hoodie");
        assertThat(review.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(reviews.approvedReviews(productId)).isEmpty();

        reviews.moderate(review.id(), true);

        assertThat(reviews.approvedReviews(productId)).extracting(Review::id).containsExactly(review.id());
        assertThat(reviews.averageRating(productId)).hasValue(5.0);
    }

    @Test
    void onlyBuyersCanReviewAndOnlyOnce() {
        assertThatThrownBy(() -> reviews.writeReview(productId, UUID.randomUUID(), 4, "never bought it"))
                .isInstanceOf(BusinessRuleException.class);

        reviews.writeReview(productId, buyer, 4, "ok");
        assertThatThrownBy(() -> reviews.writeReview(productId, buyer, 5, "again"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void ratingMustBeFromOneToFive() {
        assertThatThrownBy(() -> reviews.writeReview(productId, buyer, 6, "too good"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
