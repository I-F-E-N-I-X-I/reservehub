package io.github.ifenixi.reservehub.catalog;

import io.github.ifenixi.reservehub.common.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment")
public class CatalogController {
  public record Create(
      @NotBlank @Size(max = 120) String name,
      @NotNull @Size(max = 1000) String description,
      @Min(0) @Max(1000000) int quantity) {}

  public record Stock(@Min(-1000000) @Max(1000000) int delta) {}

  private final CatalogRepository catalog;

  public CatalogController(CatalogRepository catalog) {
    this.catalog = catalog;
  }

  @GetMapping
  public Page<Equipment> list(
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "20") int limit) {
    Page.validate(offset, limit);
    return new Page<>(catalog.list(search, offset, limit), offset, limit);
  }

  @GetMapping("/{id}")
  public Equipment get(@PathVariable long id) {
    return catalog.find(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Equipment create(@Valid @RequestBody Create input) {
    return catalog.create(input.name().strip(), input.description(), input.quantity());
  }

  @PostMapping("/{id}/stock")
  public Equipment stock(@PathVariable long id, @Valid @RequestBody Stock input) {
    return catalog.adjust(id, input.delta());
  }
}
