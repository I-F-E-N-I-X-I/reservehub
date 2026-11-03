package io.github.ifenixi.reservehub.catalog;

import io.github.ifenixi.reservehub.common.ApiException;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogRepository {
  private final JdbcTemplate jdbc;
  private final RowMapper<Equipment> mapper =
      (r, n) ->
          new Equipment(
              r.getLong("id"),
              r.getString("name"),
              r.getString("description"),
              r.getInt("total_quantity"),
              r.getInt("available_quantity"));

  public CatalogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Equipment find(long id) {
    return jdbc.query("SELECT * FROM equipment WHERE id=?", mapper, id).stream()
        .findFirst()
        .orElseThrow(ApiException::notFound);
  }

  public List<Equipment> list(String search, int offset, int limit) {
    return jdbc.query(
        "SELECT * FROM equipment WHERE LOWER(name) LIKE ? ORDER BY id LIMIT ? OFFSET ?",
        mapper,
        "%" + search.toLowerCase(Locale.ROOT) + "%",
        limit,
        offset);
  }

  public Equipment create(String name, String description, int quantity) {
    var key = new GeneratedKeyHolder();
    jdbc.update(
        c -> {
          var s =
              c.prepareStatement(
                  "INSERT INTO equipment(name,description,total_quantity,available_quantity) VALUES"
                      + " (?,?,?,?)",
                  new String[] {"id"});
          s.setString(1, name);
          s.setString(2, description);
          s.setInt(3, quantity);
          s.setInt(4, quantity);
          return s;
        },
        key);
    return find(Objects.requireNonNull(key.getKey()).longValue());
  }

  public Equipment adjust(long id, int delta) {
    int changed =
        jdbc.update(
            "UPDATE equipment SET"
                + " total_quantity=total_quantity+?,available_quantity=available_quantity+? WHERE"
                + " id=? AND available_quantity+? >= 0",
            delta,
            delta,
            id,
            delta);
    if (changed == 0) {
      find(id);
      throw ApiException.conflict("Cannot remove reserved or issued stock");
    }
    return find(id);
  }

  public void reserve(long id, int quantity) {
    if (jdbc.update(
            "UPDATE equipment SET available_quantity=available_quantity-? WHERE id=? AND"
                + " available_quantity>=?",
            quantity,
            id,
            quantity)
        == 0) {
      find(id);
      throw ApiException.conflict("Insufficient available stock");
    }
  }

  public void release(long id, int quantity) {
    jdbc.update(
        "UPDATE equipment SET available_quantity=available_quantity+? WHERE id=?", quantity, id);
  }
}
