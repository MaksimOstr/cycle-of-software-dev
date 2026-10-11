package com.cycleofsoftwaredev.reviews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cycleofsoftwaredev.ordering.api.PurchaseVerificationApi;
import com.cycleofsoftwaredev.reviews.application.ReviewService;
import com.cycleofsoftwaredev.reviews.domain.Review;
import com.cycleofsoftwaredev.reviews.domain.ReviewStatus;
import com.cycleofsoftwaredev.reviews.infrastructure.InMemoryReviewRepository;
import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import com.cycleofsoftwaredev.shared.domain.NotFoundException;
import com.cycleofsoftwaredev.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Requirements "Write a review" and "Review moderation": the rules of one review and of the product rating. */
class ReviewTest {

    private static final Instant NOW = Instant.parse("2026-10-05T10:00:00Z");

    private final UUID productId = UUID.randomUUID();

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5})
    void ratingFromOneToFiveIsAccepted(int rating) {
        assertThat(review(rating, "ok").rating()).isEqualTo(rating);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 6, 10})
    void otherRatingsAreRejected(int rating) {
        assertThatThrownBy(() -> review(rating, "ok")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textIsOptionalTrimmedAndLimitedToTwoThousandCharacters() {
        assertThat(review(5, null).text()).isEmpty();
        assertThat(review(5, "  Great hoodie \n").text()).isEqualTo("Great hoodie");
        assertThat(review(5, "a".repeat(Review.MAX_TEXT_LENGTH)).text()).hasSize(2000);
        assertThatThrownBy(() -> review(5, "a".repeat(Review.MAX_TEXT_LENGTH + 1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newReviewWaitsForModeration() {
        Review review = review(4, "ok");

        assertThat(review.status()).isEqualTo(ReviewStatus.PENDING);
        assertThat(review.productId()).isEqualTo(productId);
        assertThat(review.createdAt()).isEqualTo(NOW);
    }

    @Test
    void decisionOfTheModeratorIsFinal() {
        Review approved = review(5, "ok");
        approved.approve();
        Review rejected = review(1, "spam");
        rejected.reject();

        assertThat(approved.status()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(rejected.status()).isEqualTo(ReviewStatus.REJECTED);
        assertThatThrownBy(approved::reject).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(approved::approve).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(rejected::approve).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void ratingOfAProductIsTheAverageOfApprovedReviewsNewestFirst() {
        MutableClock clock = new MutableClock(NOW);
        PurchaseVerificationApi everybodyBought = (customerId, product) -> true;
        ReviewService reviews = new ReviewService(new InMemoryReviewRepository(), everybodyBought, clock);
        assertThat(reviews.averageRating(productId)).isEmpty();

        Review five = reviews.writeReview(productId, UUID.randomUUID(), 5, "great");
        clock.advance(Duration.ofHours(1));
        Review four = reviews.writeReview(productId, UUID.randomUUID(), 4, "good");
        clock.advance(Duration.ofHours(1));
        Review one = reviews.writeReview(productId, UUID.randomUUID(), 1, "spam");
        reviews.writeReview(productId, UUID.randomUUID(), 2, "not moderated yet");
        reviews.writeReview(UUID.randomUUID(), UUID.randomUUID(), 1, "another product");
        reviews.moderate(five.id(), true);
        reviews.moderate(four.id(), true);
        reviews.moderate(one.id(), false);

        assertThat(reviews.approvedReviews(productId)).extracting(Review::id).containsExactly(four.id(), five.id());
        assertThat(reviews.averageRating(productId)).hasValue(4.5);
        assertThatThrownBy(() -> reviews.moderate(five.id(), false)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> reviews.moderate(UUID.randomUUID(), true)).isInstanceOf(NotFoundException.class);
    }

    private Review review(int rating, String text) {
        return new Review(UUID.randomUUID(), productId, UUID.randomUUID(), rating, text, NOW);
    }
}
