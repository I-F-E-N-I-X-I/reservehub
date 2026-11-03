package io.github.ifenixi.reservehub.reservation;

import io.github.ifenixi.reservehub.common.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReservationController {
  public record Create(@Positive long equipmentId, @Min(1) @Max(1000000) int quantity) {}

  private final ReservationService service;
  private final ReservationRepository repository;

  public ReservationController(ReservationService service, ReservationRepository repository) {
    this.service = service;
    this.repository = repository;
  }

  private boolean admin(Authentication a) {
    return a.getAuthorities().stream().anyMatch(x -> x.getAuthority().equals("ROLE_ADMIN"));
  }

  @PostMapping("/reservations")
  @ResponseStatus(HttpStatus.CREATED)
  public Reservation create(@Valid @RequestBody Create input, Authentication a) {
    return service.create(a.getName(), input.equipmentId(), input.quantity());
  }

  @GetMapping("/reservations")
  public Page<Reservation> list(
      Authentication a,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "20") int limit) {
    return service.list(a.getName(), admin(a), offset, limit);
  }

  @GetMapping("/reservations/{id}")
  public Reservation get(@PathVariable long id, Authentication a) {
    return service.get(id, a.getName(), admin(a));
  }

  @GetMapping("/reservations/{id}/events")
  public List<ReservationRepository.Event> events(@PathVariable long id, Authentication a) {
    return service.events(id, a.getName(), admin(a));
  }

  @PostMapping("/reservations/{id}/cancel")
  public Reservation cancel(@PathVariable long id, Authentication a) {
    return service.transition(id, Status.CANCELLED, a.getName(), admin(a));
  }

  @PostMapping("/admin/reservations/{id}/issue")
  public Reservation issue(@PathVariable long id, Authentication a) {
    return service.transition(id, Status.ISSUED, a.getName(), true);
  }

  @PostMapping("/admin/reservations/{id}/return")
  public Reservation giveBack(@PathVariable long id, Authentication a) {
    return service.transition(id, Status.RETURNED, a.getName(), true);
  }

  @GetMapping("/admin/reports/reservations")
  public List<Map<String, Object>> summary() {
    return repository.summary();
  }
}
