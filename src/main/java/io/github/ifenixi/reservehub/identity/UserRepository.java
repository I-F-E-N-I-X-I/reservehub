package io.github.ifenixi.reservehub.identity;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  public record Account(long id, String username, String passwordHash, String role) {}

  private final JdbcTemplate jdbc;

  public UserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Account> find(String name) {
    return jdbc
        .query(
            "SELECT * FROM app_user WHERE username=?",
            (r, n) ->
                new Account(
                    r.getLong("id"),
                    r.getString("username"),
                    r.getString("password_hash"),
                    r.getString("role")),
            name)
        .stream()
        .findFirst();
  }

  public Account create(String name, String hash, String role) {
    jdbc.update(
        "INSERT INTO app_user(username,password_hash,role) VALUES (?,?,?)", name, hash, role);
    return find(name).orElseThrow();
  }
}
