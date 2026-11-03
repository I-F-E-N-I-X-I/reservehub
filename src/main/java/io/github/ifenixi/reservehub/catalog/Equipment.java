package io.github.ifenixi.reservehub.catalog;

public record Equipment(
    long id, String name, String description, int totalQuantity, int availableQuantity) {}
