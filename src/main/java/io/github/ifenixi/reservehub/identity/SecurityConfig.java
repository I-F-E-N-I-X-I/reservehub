package io.github.ifenixi.reservehub.identity;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetails(UserRepository users) {
    return name ->
        users
            .find(name)
            .map(
                u ->
                    User.withUsername(u.username())
                        .password(u.passwordHash())
                        .roles(u.role())
                        .build())
            .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/users")
                    .permitAll()
                    .requestMatchers("/api/v1/admin/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/equipment")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/api/v1/equipment/*/stock")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .httpBasic(
            b ->
                b.authenticationEntryPoint(
                    (req, res, e) -> {
                      res.setStatus(401);
                      res.setHeader("WWW-Authenticate", "Basic realm=ReserveHub");
                      res.setContentType("application/problem+json");
                      res.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\"}");
                    }))
        .exceptionHandling(
            e ->
                e.accessDeniedHandler(
                    (req, res, x) -> {
                      res.setStatus(403);
                      res.setContentType("application/problem+json");
                      res.getWriter().write("{\"status\":403,\"title\":\"Forbidden\"}");
                    }))
        .build();
  }
}
