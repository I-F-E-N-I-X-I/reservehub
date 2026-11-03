package io.github.ifenixi.reservehub.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {
  @Bean
  ApplicationRunner admin(
      UserRepository users,
      PasswordEncoder encoder,
      @Value("${app.admin.username}") String username,
      @Value("${app.admin.password}") String password) {
    return args -> {
      if (password.length() < 12
          || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
        throw new IllegalStateException(
            "Admin password must be at least 12 characters and at most 72 UTF-8 bytes");
      if (users.find(username).isEmpty()) users.create(username, encoder.encode(password), "ADMIN");
    };
  }
}
