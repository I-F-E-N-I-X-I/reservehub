package io.github.ifenixi.reservehub.common;

import java.util.List;

public record Page<T>(List<T> items, int offset, int limit) {
  public static void validate(int offset, int limit) {
    if (offset < 0 || limit < 1 || limit > 100)
      throw new ApiException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "offset >= 0 and 1 <= limit <= 100 required");
  }
}
