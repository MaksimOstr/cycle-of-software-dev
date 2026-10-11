package com.cycleofsoftwaredev.reviews.domain;

import com.cycleofsoftwaredev.shared.domain.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Customer review of a product. It is shown in the store only after moderation. */
@Entity
@Table(name = "reviews", schema = "reviews")
public class Review {

    public static final int MAX_TEXT_LENGTH = 2000;

    @Id
    private UUID id;
    private UUID productId;
    private UUID customerId;
    private int rating;

    @Column(name = "review_text", length = MAX_TEXT_LENGTH)
    private String text;

    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    private ReviewStatus status = ReviewStatus.PENDING;

    protected Review() {
        // for JPA
    }

    public Review(UUID id, UUID productId, UUID customerId, int rating, String text, Instant createdAt) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        if (text != null && text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Review text must not exceed " + MAX_TEXT_LENGTH + " characters");
        }
        this.id = Objects.requireNonNull(id);
        this.productId = Objects.requireNonNull(productId);
        this.customerId = Objects.requireNonNull(customerId);
        this.rating = rating;
        this.text = text == null ? "" : text.trim();
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public void approve() {
        requirePending();
        status = ReviewStatus.APPROVED;
    }

    public void reject() {
        requirePending();
        status = ReviewStatus.REJECTED;
    }

    private void requirePending() {
        if (status != ReviewStatus.PENDING) {
            throw new BusinessRuleException("Review " + id + " is already " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID productId() {
        return productId;
    }

    public UUID customerId() {
        return customerId;
    }

    public int rating() {
        return rating;
    }

    public String text() {
        return text;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public ReviewStatus status() {
        return status;
    }
}
