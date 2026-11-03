package io.github.ifenixi.reservehub.reservation;

import java.time.OffsetDateTime;

public record Reservation(
    long id,
    long userId,
    long equipmentId,
    int quantity,
    Status status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt) {}
