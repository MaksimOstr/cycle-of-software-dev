package com.cycleofsoftwaredev.web;

import com.cycleofsoftwaredev.reviews.application.ReviewService;
import com.cycleofsoftwaredev.reviews.domain.Review;
import com.cycleofsoftwaredev.reviews.domain.ReviewStatus;
import com.cycleofsoftwaredev.shared.domain.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviews;
    private final CurrentUserResolver currentUser;

    public ReviewController(ReviewService reviews, CurrentUserResolver currentUser) {
        this.reviews = reviews;
        this.currentUser = currentUser;
    }

    public record ReviewRequest(@Min(1) @Max(5) int rating, @Size(max = 2000) String text) {
    }

    public record ModerationRequest(boolean approved) {
    }

    public record ReviewResponse(UUID id, UUID productId, int rating, String text, ReviewStatus status, Instant createdAt) {

        static ReviewResponse of(Review review) {
            return new ReviewResponse(review.id(), review.productId(), review.rating(), review.text(),
                    review.status(), review.createdAt());
        }
    }

    @GetMapping("/products/{productId}/reviews")
    public List<ReviewResponse> approvedReviews(@PathVariable UUID productId) {
        return reviews.approvedReviews(productId).stream().map(ReviewResponse::of).toList();
    }

    @PostMapping("/products/{productId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse write(@PathVariable UUID productId,
                                @RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
                                @Valid @RequestBody ReviewRequest request) {
        Actor customer = currentUser.requireUser(userId);
        return ReviewResponse.of(reviews.writeReview(productId, customer.userId(), request.rating(), request.text()));
    }

    @PatchMapping("/management/reviews/{reviewId}")
    public ReviewResponse moderate(@PathVariable UUID reviewId,
                                   @RequestHeader(value = CurrentUserResolver.HEADER, required = false) UUID userId,
                                   @RequestBody ModerationRequest request) {
        currentUser.requireStaff(userId);
        return ReviewResponse.of(reviews.moderate(reviewId, request.approved()));
    }
}
