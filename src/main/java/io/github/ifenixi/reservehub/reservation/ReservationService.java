package io.github.ifenixi.reservehub.reservation;

import io.github.ifenixi.reservehub.catalog.CatalogRepository;
import io.github.ifenixi.reservehub.common.*;
import io.github.ifenixi.reservehub.identity.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {
  private final ReservationRepository reservations;
  private final CatalogRepository catalog;
  private final UserRepository users;

  public ReservationService(
      ReservationRepository reservations, CatalogRepository catalog, UserRepository users) {
    this.reservations = reservations;
    this.catalog = catalog;
    this.users = users;
  }

  @Transactional
  public Reservation create(String username, long equipmentId, int quantity) {
    if (quantity < 1 || quantity > 1000000)
      throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid quantity");
    var user = users.find(username).orElseThrow(ApiException::notFound);
    catalog.reserve(equipmentId, quantity);
    var reservation = reservations.create(user.id(), equipmentId, quantity);
    reservations.event(reservation.id(), username, null, Status.RESERVED);
    return reservation;
  }

  public Reservation get(long id, String username, boolean admin) {
    var r = reservations.find(id, false);
    authorize(r, username, admin);
    return r;
  }

  public Page<Reservation> list(String username, boolean admin, int offset, int limit) {
    Page.validate(offset, limit);
    long userId = users.find(username).orElseThrow(ApiException::notFound).id();
    return new Page<>(reservations.list(userId, admin, offset, limit), offset, limit);
  }

  public List<ReservationRepository.Event> events(long id, String username, boolean admin) {
    get(id, username, admin);
    return reservations.events(id);
  }

  @Transactional
  public Reservation transition(long id, Status target, String username, boolean admin) {
    // Serialize transitions so repeated cancellation/return cannot release stock twice.
    var r = reservations.find(id, true);
    authorize(r, username, admin);
    if (target != Status.CANCELLED && !admin)
      throw new ApiException(
          org.springframework.http.HttpStatus.FORBIDDEN, "Administrator role required");
    r.status().requireTransitionTo(target);
    if (target == Status.CANCELLED || target == Status.RETURNED)
      catalog.release(r.equipmentId(), r.quantity());
    var result = reservations.change(id, target);
    reservations.event(id, username, r.status(), target);
    return result;
  }

  private void authorize(Reservation r, String username, boolean admin) {
    if (!admin && users.find(username).orElseThrow(ApiException::notFound).id() != r.userId())
      throw ApiException.notFound();
  }
}
