package io.github.ifenixi.reservehub;

import static org.junit.jupiter.api.Assertions.*;

import io.github.ifenixi.reservehub.catalog.CatalogRepository;
import io.github.ifenixi.reservehub.common.ApiException;
import io.github.ifenixi.reservehub.identity.UserRepository;
import io.github.ifenixi.reservehub.reservation.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ReservationIntegrationTest {
  @Autowired ReservationService service;
  @Autowired ReservationRepository reservations;
  @Autowired CatalogRepository catalog;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;
  String alice, bob;

  @BeforeEach
  void accounts() {
    alice = "a" + UUID.randomUUID();
    bob = "b" + UUID.randomUUID();
    users.create(alice, "unused", "USER");
    users.create(bob, "unused", "USER");
  }

  @Test
  void completeLifecycleRestoresStockAndRecordsAudit() {
    var item = catalog.create("Camera", "Test", 3);
    var r = service.create(alice, item.id(), 2);
    assertEquals(1, catalog.find(item.id()).availableQuantity());
    assertEquals(Status.ISSUED, service.transition(r.id(), Status.ISSUED, "admin", true).status());
    assertEquals(1, catalog.find(item.id()).availableQuantity());
    assertEquals(
        Status.RETURNED, service.transition(r.id(), Status.RETURNED, "admin", true).status());
    assertEquals(3, catalog.find(item.id()).availableQuantity());
    assertEquals(
        List.of("RESERVED", "ISSUED", "RETURNED"),
        service.events(r.id(), alice, false).stream()
            .map(ReservationRepository.Event::toStatus)
            .toList());
  }

  @Test
  void repeatedCancelDoesNotDuplicateStock() {
    var item = catalog.create("Laptop", "", 2);
    var r = service.create(alice, item.id(), 1);
    service.transition(r.id(), Status.CANCELLED, alice, false);
    assertThrows(
        ApiException.class, () -> service.transition(r.id(), Status.CANCELLED, alice, false));
    assertEquals(2, catalog.find(item.id()).availableQuantity());
    assertEquals(2, reservations.events(r.id()).size());
  }

  @Test
  void otherUserCannotReadCancelOrReadEvents() {
    var item = catalog.create("Laptop", "", 2);
    var r = service.create(alice, item.id(), 1);
    assertThrows(ApiException.class, () -> service.get(r.id(), bob, false));
    assertThrows(ApiException.class, () -> service.events(r.id(), bob, false));
    assertThrows(
        ApiException.class, () -> service.transition(r.id(), Status.CANCELLED, bob, false));
    assertEquals(1, catalog.find(item.id()).availableQuantity());
    assertTrue(service.list(bob, false, 0, 20).items().isEmpty());
  }

  @Test
  void userCannotIssueEquipment() {
    var item = catalog.create("Laptop", "", 2);
    var r = service.create(alice, item.id(), 1);
    assertThrows(ApiException.class, () -> service.transition(r.id(), Status.ISSUED, alice, false));
    assertEquals(Status.RESERVED, reservations.find(r.id(), false).status());
  }

  @Test
  void insufficientStockLeavesNoReservationOrEvent() {
    var item = catalog.create("Laptop", "", 1);
    assertThrows(ApiException.class, () -> service.create(alice, item.id(), 2));
    assertEquals(1, catalog.find(item.id()).availableQuantity());
    assertTrue(service.list(alice, false, 0, 20).items().isEmpty());
  }

  @Test
  void stockCannotBeRemovedWhileReserved() {
    var item = catalog.create("Laptop", "", 3);
    service.create(alice, item.id(), 2);
    assertThrows(ApiException.class, () -> catalog.adjust(item.id(), -2));
    assertEquals(3, catalog.find(item.id()).totalQuantity());
    assertEquals(0, catalog.adjust(item.id(), -1).availableQuantity());
  }

  @Test
  void outerRollbackRevertsStockReservationAndAudit() {
    var item = catalog.create("Laptop", "", 2);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              service.create(alice, item.id(), 1);
              tx.setRollbackOnly();
            });
    assertEquals(2, catalog.find(item.id()).availableQuantity());
    assertTrue(service.list(alice, false, 0, 20).items().isEmpty());
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM reservation_event e JOIN reservation r ON r.id=e.reservation_id"
                + " WHERE r.equipment_id=?",
            Integer.class,
            item.id()));
  }

  @Test
  void concurrentReservationsNeverOversell() throws Exception {
    var item = catalog.create("Last camera", "", 1);
    try (var pool = Executors.newFixedThreadPool(8)) {
      var start = new CountDownLatch(1);
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 8; i++)
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    service.create(alice, item.id(), 1);
                    return true;
                  } catch (ApiException e) {
                    assertEquals(409, e.status().value());
                    return false;
                  }
                }));
      start.countDown();
      int successes = 0;
      for (var future : results) if (future.get(15, TimeUnit.SECONDS)) successes++;
      assertEquals(1, successes);
      assertEquals(0, catalog.find(item.id()).availableQuantity());
    }
  }

  @Test
  void concurrentCancellationReleasesOnce() throws Exception {
    var item = catalog.create("Last camera", "", 1);
    var r = service.create(alice, item.id(), 1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      List<Future<Boolean>> results = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  try {
                    service.transition(r.id(), Status.CANCELLED, alice, false);
                    return true;
                  } catch (ApiException e) {
                    assertEquals(409, e.status().value());
                    return false;
                  }
                }));
      start.countDown();
      int successes = 0;
      for (var f : results) if (f.get(15, TimeUnit.SECONDS)) successes++;
      assertEquals(1, successes);
      assertEquals(1, catalog.find(item.id()).availableQuantity());
      assertEquals(2, reservations.events(r.id()).size());
    }
  }

  @Test
  void missingEquipmentReturnsNotFound() {
    assertEquals(
        404,
        assertThrows(ApiException.class, () -> service.create(alice, Long.MAX_VALUE, 1))
            .status()
            .value());
  }
}
