package io.github.ifenixi.reservehub;

import static org.junit.jupiter.api.Assertions.*;

import io.github.ifenixi.reservehub.common.ApiException;
import io.github.ifenixi.reservehub.reservation.Status;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StatusTest {
  @ParameterizedTest
  @CsvSource({"RESERVED,ISSUED", "RESERVED,CANCELLED", "ISSUED,RETURNED"})
  void allowsBusinessTransitions(Status from, Status to) {
    assertDoesNotThrow(() -> from.requireTransitionTo(to));
  }

  @ParameterizedTest
  @CsvSource({
    "RESERVED,RETURNED",
    "RESERVED,RESERVED",
    "ISSUED,CANCELLED",
    "ISSUED,RESERVED",
    "ISSUED,ISSUED",
    "RETURNED,RESERVED",
    "RETURNED,ISSUED",
    "RETURNED,RETURNED",
    "RETURNED,CANCELLED",
    "CANCELLED,RESERVED",
    "CANCELLED,ISSUED",
    "CANCELLED,RETURNED",
    "CANCELLED,CANCELLED"
  })
  void rejectsInvalidTransitions(Status from, Status to) {
    assertThrows(ApiException.class, () -> from.requireTransitionTo(to));
  }
}
