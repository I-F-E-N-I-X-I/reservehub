package io.github.ifenixi.reservehub;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.github.ifenixi.reservehub.identity.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired PasswordEncoder passwords;

  @Test
  void healthIsPublic() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void anonymousCannotReadCatalog() throws Exception {
    mvc.perform(get("/api/v1/equipment")).andExpect(status().isUnauthorized());
  }

  @Test
  void wrongPasswordIsRejected() throws Exception {
    mvc.perform(get("/api/v1/equipment").with(httpBasic("admin", "wrong")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void basicAuthenticationLoadsDatabaseUser() throws Exception {
    mvc.perform(get("/api/v1/equipment").with(httpBasic("admin", "test-admin-password")))
        .andExpect(status().isOk());
  }

  @Test
  void registrationHashesPasswordAndDoesNotExposeHash() throws Exception {
    String name = "user" + UUID.randomUUID();
    mvc.perform(
            post("/api/v1/users")
                .contentType("application/json")
                .content("{\"username\":\"" + name + "\",\"password\":\"a-long-password\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("role").value("USER"))
        .andExpect(jsonPath("passwordHash").doesNotExist());
    assertTrue(passwords.matches("a-long-password", users.find(name).orElseThrow().passwordHash()));
  }

  @Test
  void roleInjectionIsRejected() throws Exception {
    mvc.perform(
            post("/api/v1/users")
                .contentType("application/json")
                .content(
                    "{\"username\":\"intruder\",\"password\":\"a-long-password\",\"role\":\"ADMIN\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void duplicateUsernameIsConflict() throws Exception {
    mvc.perform(
            post("/api/v1/users")
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"a-long-password\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("status").value(409));
  }

  @Test
  void shortPasswordRejected() throws Exception {
    mvc.perform(
            post("/api/v1/users")
                .contentType("application/json")
                .content("{\"username\":\"alice\",\"password\":\"short\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void userCannotCreateEquipment() throws Exception {
    mvc.perform(
            post("/api/v1/equipment")
                .with(user("alice"))
                .contentType("application/json")
                .content("{\"name\":\"Camera\",\"description\":\"\",\"quantity\":1}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void userCannotAccessAdminReport() throws Exception {
    mvc.perform(get("/api/v1/admin/reports/reservations").with(user("alice")))
        .andExpect(status().isForbidden());
  }

  @Test
  void invalidQuantityRejected() throws Exception {
    mvc.perform(
            post("/api/v1/reservations")
                .with(user("alice"))
                .contentType("application/json")
                .content("{\"equipmentId\":1,\"quantity\":0}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void invalidPaginationRejected() throws Exception {
    mvc.perform(get("/api/v1/equipment?limit=101").with(user("alice")))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/equipment?offset=-1").with(user("alice")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void badIdRejected() throws Exception {
    mvc.perform(get("/api/v1/equipment/abc").with(user("alice")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void emptyEquipmentNameRejected() throws Exception {
    mvc.perform(
            post("/api/v1/equipment")
                .with(user("admin").roles("ADMIN"))
                .contentType("application/json")
                .content("{\"name\":\"  \",\"description\":\"\",\"quantity\":1}"))
        .andExpect(status().isBadRequest());
  }
}
