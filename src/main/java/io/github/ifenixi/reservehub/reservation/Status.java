package io.github.ifenixi.reservehub.reservation;

import io.github.ifenixi.reservehub.common.ApiException;

public enum Status {
  RESERVED,
  ISSUED,
  RETURNED,
  CANCELLED;

  public void requireTransitionTo(Status target) {
    boolean allowed =
        this == RESERVED && (target == ISSUED || target == CANCELLED)
            || this == ISSUED && target == RETURNED;
    if (!allowed)
      throw ApiException.conflict("Transition from " + this + " to " + target + " is not allowed");
  }
}
