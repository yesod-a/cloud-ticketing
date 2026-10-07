package com.cloudticket.inventory.persistence;

import com.cloudticket.inventory.persistence.entity.AdmissionTicketEntity;
import com.cloudticket.inventory.persistence.mapper.AdmissionTicketMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class AdmissionTicketRepository {
  private final AdmissionTicketMapper tickets;

  public AdmissionTicketRepository(AdmissionTicketMapper tickets) { this.tickets = tickets; }

  public void insertHeld(String orderId, String userId, String sessionId, List<Long> numbers, Instant expiresAt) {
    for (Long number : numbers) {
      AdmissionTicketEntity ticket = new AdmissionTicketEntity();
      ticket.setId(UUID.randomUUID().toString());
      ticket.setOrderId(orderId);
      ticket.setUserId(userId);
      ticket.setSessionId(sessionId);
      ticket.setTicketNumber(number);
      ticket.setState("HELD");
      ticket.setExpiresAt(expiresAt);
      tickets.insert(ticket);
    }
  }

  public List<AdmissionTicketEntity> findHeldByOrder(String orderId) { return tickets.selectHeldByOrder(orderId); }
  public List<AdmissionTicketEntity> findActiveByOrder(String orderId) { return tickets.selectActiveByOrder(orderId); }
  public List<String> expiredOrderIds() { return tickets.selectExpiredOrderIds(); }
  public int markSold(String orderId) { return tickets.markSold(orderId); }
  public int markReleased(String orderId) { return tickets.markReleased(orderId); }
  public int markReleasedActive(String orderId) { return tickets.markReleasedActive(orderId); }
}
