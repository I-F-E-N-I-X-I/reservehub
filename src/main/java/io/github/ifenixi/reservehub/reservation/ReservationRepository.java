package io.github.ifenixi.reservehub.reservation;

import io.github.ifenixi.reservehub.common.ApiException;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationRepository {
  public record Event(
      long id, String actor, String fromStatus, String toStatus, OffsetDateTime occurredAt) {}

  private final JdbcTemplate jdbc;
  private final RowMapper<Reservation> mapper =
      (r, n) ->
          new Reservation(
              r.getLong("id"),
              r.getLong("user_id"),
              r.getLong("equipment_id"),
              r.getInt("quantity"),
              Status.valueOf(r.getString("status")),
              r.getObject("created_at", OffsetDateTime.class),
              r.getObject("updated_at", OffsetDateTime.class));

  public ReservationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Reservation find(long id, boolean lock) {
    return jdbc
        .query("SELECT * FROM reservation WHERE id=?" + (lock ? " FOR UPDATE" : ""), mapper, id)
        .stream()
        .findFirst()
        .orElseThrow(ApiException::notFound);
  }

  public List<Reservation> list(long userId, boolean admin, int offset, int limit) {
    return admin
        ? jdbc.query(
            "SELECT * FROM reservation ORDER BY id DESC LIMIT ? OFFSET ?", mapper, limit, offset)
        : jdbc.query(
            "SELECT * FROM reservation WHERE user_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
            mapper,
            userId,
            limit,
            offset);
  }

  public Reservation create(long userId, long equipmentId, int quantity) {
    var key = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var s =
              c.prepareStatement(
                  "INSERT INTO reservation(user_id,equipment_id,quantity,status) VALUES"
                      + " (?,?,?,'RESERVED')",
                  new String[] {"id"});
          s.setLong(1, userId);
          s.setLong(2, equipmentId);
          s.setInt(3, quantity);
          return s;
        },
        key);
    return find(Objects.requireNonNull(key.getKey()).longValue(), false);
  }

  public Reservation change(long id, Status status) {
    jdbc.update(
        "UPDATE reservation SET status=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
        status.name(),
        id);
    return find(id, false);
  }

  public void event(long id, String actor, Status from, Status to) {
    jdbc.update(
        "INSERT INTO reservation_event(reservation_id,actor,from_status,to_status) VALUES"
            + " (?,?,?,?)",
        id,
        actor,
        from == null ? null : from.name(),
        to.name());
  }

  public List<Event> events(long id) {
    return jdbc.query(
        "SELECT * FROM reservation_event WHERE reservation_id=? ORDER BY id",
        (r, n) ->
            new Event(
                r.getLong("id"),
                r.getString("actor"),
                r.getString("from_status"),
                r.getString("to_status"),
                r.getObject("occurred_at", OffsetDateTime.class)),
        id);
  }

  public List<Map<String, Object>> summary() {
    return jdbc.queryForList(
        "SELECT status,COUNT(*) AS reservation_count,COALESCE(SUM(quantity),0) AS unit_count FROM"
            + " reservation GROUP BY status ORDER BY status");
  }
}
