package io.github.ifenixi.reservehub.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class IdentityController {
  public record Register(
      @NotBlank @Pattern(regexp = "[a-zA-Z0-9_-]{3,64}") String username,
      @NotBlank @Size(min = 12, max = 64) @Pattern(regexp = "[!-~]+") String password) {}

  public record UserView(long id, String username, String role) {}

  private final UserRepository users;
  private final PasswordEncoder passwords;

  public IdentityController(UserRepository users, PasswordEncoder passwords) {
    this.users = users;
    this.passwords = passwords;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserView register(@Valid @RequestBody Register input) {
    var user = users.create(input.username(), passwords.encode(input.password()), "USER");
    return new UserView(user.id(), user.username(), user.role());
  }
}
