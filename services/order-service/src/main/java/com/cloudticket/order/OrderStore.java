package com.cloudticket.order;

import com.cloudticket.common.events.EventTypes;
import com.cloudticket.order.client.InventoryReservationClient;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;

@Repository
public class OrderStore {
  private final JdbcTemplate jdbc;
  private final InventoryReservationClient inventory;
  public OrderStore(JdbcTemplate jdbc) { this(jdbc, null); }
  @org.springframework.beans.factory.annotation.Autowired
  public OrderStore(JdbcTemplate jdbc, InventoryReservationClient inventory) { this.jdbc = jdbc; this.inventory = inventory; }

  @Transactional
  public Map<String, Object> create(String userId, String sessionId, String seatIds, String idempotencyKey) {
    if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId required");
    if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId required");
    if (seatIds == null || seatIds.isBlank()) throw new IllegalArgumentException("seatIds required");
    if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey required");
    String key = idempotencyKey.trim();
    List<String> requestedSeats = normalizeSeats(seatIds);
    String requestHash = requestHash(userId, sessionId, requestedSeats);
    List<Map<String, Object>> existing = jdbc.query("SELECT id,user_id,session_id,seat_ids,status,created_at,updated_at,request_hash FROM ticket_order WHERE idempotency_key=?", this::map, key);
    if (!existing.isEmpty()) {
      Map<String, Object> previous = existing.get(0);
      String storedHash = Objects.toString(previous.get("requestHash"), "");
      if (storedHash.isBlank()) {
        storedHash = requestHash(String.valueOf(previous.get("userId")), String.valueOf(previous.get("sessionId")), normalizeSeats(String.valueOf(previous.get("seatIds"))));
      }
      if (!requestHash.equals(storedHash)) throw new IdempotencyConflictException();
      return previous;
    }
    String id = UUID.randomUUID().toString();
    if (inventory != null) inventory.reserve(id, sessionId, requestedSeats);
    try {
      jdbc.update("INSERT INTO ticket_order(id,user_id,session_id,seat_ids,idempotency_key,request_hash,status) VALUES (?,?,?,?,?,?, 'PENDING')", id, userId, sessionId, String.join(",", requestedSeats), key, requestHash);
    } catch (DuplicateKeyException duplicate) {
      if (inventory != null) inventory.release(id);
      return findByIdempotencyKey(key).orElseThrow(() -> duplicate);
    } catch (RuntimeException failure) {
      if (inventory != null) inventory.release(id);
      throw failure;
    }
    Map<String, Object> created = find(id).orElseThrow(() -> new IllegalStateException("order was not persisted"));
    writeOutbox(EventTypes.ORDER_CREATED, id, created);
    return created;
  }

  public Optional<Map<String, Object>> find(String id) { return jdbc.query("SELECT id,user_id,session_id,seat_ids,status,created_at,updated_at,request_hash FROM ticket_order WHERE id=?", this::map, id).stream().findFirst(); }
  public Optional<Map<String, Object>> findByIdempotencyKey(String key) { return jdbc.query("SELECT id,user_id,session_id,seat_ids,status,created_at,updated_at,request_hash FROM ticket_order WHERE idempotency_key=?", this::map, key).stream().findFirst(); }
  public Page pageForUser(String userId, int page, int size) { return page("user_id=?", new Object[]{userId}, page, size); }
  public Page pageForAdmin(String status, int page, int size) { String st = status == null ? "" : status.trim(); return page("(?='' OR status=?)", new Object[]{st, st}, page, size); }
  public Page pageForAdmin(String status, int page, int size, String permissions, String scopes) {
    if (permissions != null && permissions.contains("system:config")) return pageForAdmin(status, page, size);
    String st = status == null ? "" : status.trim(); int p=Math.max(0,page), s=Math.min(100,Math.max(1,size));
    List<Map<String,Object>> all = jdbc.query("SELECT id,user_id,session_id,seat_ids,status,created_at,updated_at,request_hash FROM ticket_order WHERE (?='' OR status=?) ORDER BY created_at DESC", this::map, st, st);
    List<Map<String,Object>> visible = all.stream().filter(order -> OrderScopeFilter.visible(order, permissions, scopes)).toList();
    int from=Math.min(p*s,visible.size()); return new Page(visible.subList(from,Math.min(from+s,visible.size())),p,s,visible.size());
  }

  private Page page(String where, Object[] args, int page, int size) {
    int p = Math.max(0, page), s = Math.min(100, Math.max(1, size));
    Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM ticket_order WHERE " + where, Integer.class, args);
    Object[] listArgs = Arrays.copyOf(args, args.length + 2); listArgs[args.length] = s; listArgs[args.length + 1] = p * s;
    List<Map<String, Object>> items = jdbc.query("SELECT id,user_id,session_id,seat_ids,status,created_at,updated_at,request_hash FROM ticket_order WHERE " + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?", this::map, listArgs);
    return new Page(items, p, s, total == null ? 0 : total);
  }

  @Transactional
  public Map<String, Object> cancel(String id) {
    int changed = jdbc.update("UPDATE ticket_order SET status='CANCELLED' WHERE id=? AND status IN ('PENDING','PAID')", id);
    if (changed == 0) throw new IllegalStateException("order cannot be cancelled");
    if (inventory != null) inventory.release(id);
    Map<String, Object> cancelled = find(id).orElseThrow();
    writeOutbox(EventTypes.ORDER_CANCELLED, id, cancelled);
    return cancelled;
  }

  public Map<String, Object> requestRefund(String orderId, String userId, String reason) {
    if (reason == null || reason.isBlank()) throw new IllegalArgumentException("refund reason required");
    Map<String,Object> order = find(orderId).filter(o -> userId.equals(o.get("userId"))).orElseThrow(() -> new NoSuchElementException("order not found"));
    if (!Set.of("PAID", "PENDING").contains(String.valueOf(order.get("status")))) throw new IllegalStateException("order is not refundable");
    String id = UUID.randomUUID().toString();
    try { jdbc.update("INSERT INTO refund_request(id,order_id,user_id,reason,status) VALUES (?,?,?,?,'REQUESTED')", id, orderId, userId, reason.trim()); }
    catch (DuplicateKeyException e) { return refundByOrder(orderId).orElseThrow(() -> e); }
    return refund(id).orElseThrow();
  }

  public Optional<Map<String,Object>> refund(String id) { return jdbc.query("SELECT id,order_id,user_id,reason,status,reviewed_by,reviewed_at,created_at FROM refund_request WHERE id=?", (r,n) -> refundMap(r), id).stream().findFirst(); }
  public Optional<Map<String,Object>> refundByOrder(String orderId) { return jdbc.query("SELECT id,order_id,user_id,reason,status,reviewed_by,reviewed_at,created_at FROM refund_request WHERE order_id=?", (r,n) -> refundMap(r), orderId).stream().findFirst(); }
  public Page refunds(String status, int page, int size) {
    String st=status==null?"":status.trim(); int p=Math.max(0,page),s=Math.min(100,Math.max(1,size));
    Integer total=jdbc.queryForObject("SELECT COUNT(*) FROM refund_request WHERE (?='' OR status=?)",Integer.class,st,st);
    var items=jdbc.query("SELECT id,order_id,user_id,reason,status,reviewed_by,reviewed_at,created_at FROM refund_request WHERE (?='' OR status=?) ORDER BY created_at DESC LIMIT ? OFFSET ?",(r,n)->refundMap(r),st,st,s,p*s);
    return new Page(items,p,s,total==null?0:total);
  }
  public Page refunds(String status, int page, int size, String permissions, String scopes) {
    if (permissions != null && permissions.contains("system:config")) return refunds(status,page,size);
    String st=status==null?"":status.trim(); int p=Math.max(0,page),s=Math.min(100,Math.max(1,size));
    String scope=" AND (FIND_IN_SET(CONCAT('SESSION:',o.session_id),REPLACE(?,' ',''))>0 OR FIND_IN_SET('SESSION:*',REPLACE(?,' ',''))>0)";
    Integer total=jdbc.queryForObject("SELECT COUNT(*) FROM refund_request r JOIN ticket_order o ON o.id=r.order_id WHERE (?='' OR r.status=?)"+scope,Integer.class,st,st,scopes,scopes);
    var items=jdbc.query("SELECT r.id,r.order_id,r.user_id,r.reason,r.status,r.reviewed_by,r.reviewed_at,r.created_at FROM refund_request r JOIN ticket_order o ON o.id=r.order_id WHERE (?='' OR r.status=?)"+scope+" ORDER BY r.created_at DESC LIMIT ? OFFSET ?",(r,n)->refundMap(r),st,st,scopes,scopes,s,p*s);
    return new Page(items,p,s,total==null?0:total);
  }
  @Transactional
  public Map<String,Object> reviewRefund(String id, boolean approve, String reviewer) {
    Map<String,Object> request=refund(id).orElseThrow(() -> new NoSuchElementException("refund request not found"));
    if (!"REQUESTED".equals(request.get("status"))) throw new IllegalStateException("refund request already reviewed");
    String next=approve?"APPROVED":"REJECTED";
    int changed=jdbc.update("UPDATE refund_request SET status=?,reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='REQUESTED'",next,reviewer,id);
    if (changed==0) throw new IllegalStateException("refund request already reviewed");
    if (approve) {
      String orderId = String.valueOf(request.get("orderId"));
      int orderChanged = jdbc.update("UPDATE ticket_order SET status='REFUNDED' WHERE id=? AND status IN ('PAID','PENDING')", orderId);
      if (orderChanged > 0) {
        writeOutbox(EventTypes.ORDER_REFUNDED, orderId, Map.of(
            "orderId", orderId, "refundId", String.valueOf(request.get("id")),
            "reviewedBy", reviewer, "status", "REFUNDED"));
      }
      if (inventory != null) inventory.release(orderId);
    }
    return refund(id).orElseThrow();
  }

  private void writeOutbox(String eventType, String aggregateId, Map<String, Object> payload) {
    StringBuilder json = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, Object> entry : payload.entrySet()) {
      if (!first) json.append(',');
      first = false;
      json.append('"').append(jsonEscape(entry.getKey())).append("\":\"")
          .append(jsonEscape(String.valueOf(entry.getValue()))).append('"');
    }
    json.append('}');
    jdbc.update(
        "INSERT INTO order_outbox(event_id,event_type,aggregate_type,aggregate_id,payload,trace_id,schema_version) VALUES (?,?, 'ORDER', ?,?,?,1)",
        UUID.randomUUID().toString(), eventType, aggregateId, json.toString(), traceId());
  }

  private static String traceId() {
    String trace = MDC.get("traceId");
    return trace == null || trace.isBlank() ? null : trace;
  }

  private static String jsonEscape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static List<String> normalizeSeats(String rawSeatIds) {
    String[] parts = rawSeatIds == null ? new String[0] : rawSeatIds.split(",");
    List<String> seats = Arrays.stream(parts).map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    if (seats.isEmpty() || seats.size() > 6 || seats.size() != parts.length) throw new IllegalArgumentException("seatIds must contain 1-6 unique values");
    return seats;
  }

  private static String requestHash(String userId, String sessionId, List<String> seats) {
    String input = userId + "|" + sessionId + "|" + String.join(",", seats);
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(digest.length * 2);
      for (byte value : digest) hex.append(String.format("%02x", value));
      return hex.toString();
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }
  private Map<String,Object> refundMap(ResultSet r) throws SQLException { Map<String,Object> v=new LinkedHashMap<>(); v.put("id",r.getString("id")); v.put("orderId",r.getString("order_id")); v.put("userId",r.getString("user_id")); v.put("reason",r.getString("reason")); v.put("status",r.getString("status")); v.put("reviewedBy",Objects.toString(r.getString("reviewed_by"),"")); v.put("reviewedAt",r.getTimestamp("reviewed_at")==null?null:r.getTimestamp("reviewed_at").toInstant().toString()); v.put("createdAt",r.getTimestamp("created_at").toInstant().toString()); return v; }

  private Map<String, Object> map(ResultSet r, int ignored) throws SQLException {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", r.getString("id")); value.put("userId", r.getString("user_id")); value.put("sessionId", r.getString("session_id"));
    value.put("seatIds", r.getString("seat_ids")); value.put("status", r.getString("status"));
    value.put("requestHash", r.getString("request_hash"));
    value.put("createdAt", r.getTimestamp("created_at").toInstant().toString()); value.put("updatedAt", r.getTimestamp("updated_at").toInstant().toString());
    return value;
  }

  public record Page(List<Map<String, Object>> items, int page, int size, long total) {
    public long totalPages() { return total == 0 ? 0 : (total + size - 1) / size; }
    public Map<String, Object> asMap() { return Map.of("items", items, "page", page, "size", size, "total", total, "totalPages", totalPages()); }
  }
}
