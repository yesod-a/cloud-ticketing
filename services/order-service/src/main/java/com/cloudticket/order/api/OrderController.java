package com.cloudticket.order.api;

import com.cloudticket.order.OrderStore;
import com.cloudticket.order.security.OrderAuthorization;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
  private final OrderStore store;
  private final OrderAuthorization authorization = new OrderAuthorization();
  public OrderController(OrderStore store) { this.store = store; }

  @PostMapping
  public Map<String, Object> create(@RequestHeader("X-User-Id") String user, @RequestBody Map<String, Object> body) {
    return store.create(user, String.valueOf(body.getOrDefault("sessionId", "")), String.valueOf(body.getOrDefault("seatIds", "")), String.valueOf(body.getOrDefault("idempotencyKey", "")));
  }

  @GetMapping("/me")
  public Map<String, Object> mine(@RequestHeader("X-User-Id") String user, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) {
    return store.pageForUser(user, page, size).asMap();
  }

  @GetMapping("/admin")
  public Map<String, Object> admin(@RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions, @RequestHeader(value = "X-User-Scopes", defaultValue = "") String scopes, @RequestParam(name = "status", defaultValue = "") String status, @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "20") int size) {
    if (!permissions.contains("order:read") && !permissions.contains("system:config")) throw new SecurityException("forbidden");
    return store.pageForAdmin(status, page, size, permissions, scopes).asMap();
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable("id") String id, @RequestHeader("X-User-Id") String user, @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions, @RequestHeader(value = "X-User-Scopes", defaultValue = "") String scopes) {
    return store.find(id).filter(order -> authorization.canRead(user, String.valueOf(order.get("userId")), permissions, scopes, "SESSION", String.valueOf(order.get("sessionId")))).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<?> cancel(@PathVariable("id") String id, @RequestHeader("X-User-Id") String user, @RequestHeader(value = "X-User-Permissions", defaultValue = "") String permissions, @RequestHeader(value = "X-User-Scopes", defaultValue = "") String scopes) {
    return store.find(id).filter(order -> authorization.canWrite(user, String.valueOf(order.get("userId")), permissions, scopes, "SESSION", String.valueOf(order.get("sessionId")))).map(order -> ResponseEntity.ok(store.cancel(id))).orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/{id}/refund")
  public ResponseEntity<?> requestRefund(@PathVariable("id") String id, @RequestHeader("X-User-Id") String user, @RequestBody Map<String,String> body) {
    return store.find(id).filter(order -> authorization.canRequestRefund(user, String.valueOf(order.get("userId")))).map(order -> ResponseEntity.ok(store.requestRefund(id, user, body.get("reason")))).orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/admin/refunds")
  public Map<String,Object> refunds(@RequestHeader(value="X-User-Permissions",defaultValue="") String permissions, @RequestHeader(value="X-User-Scopes",defaultValue="") String scopes, @RequestParam(name="status",defaultValue="") String status, @RequestParam(name="page",defaultValue="0") int page, @RequestParam(name="size",defaultValue="20") int size) {
    if (!authorization.canReviewRefund(permissions)) throw new SecurityException("forbidden");
    return store.refunds(status,page,size,permissions,scopes).asMap();
  }

  @PostMapping("/admin/refunds/{id}/approve")
  public Map<String,Object> approveRefund(@PathVariable("id") String id, @RequestHeader(value="X-User-Permissions",defaultValue="") String permissions, @RequestHeader(value="X-User-Scopes",defaultValue="") String scopes, @RequestHeader(value="X-User-Id",defaultValue="") String reviewer) {
    requireRefundScope(id, permissions, scopes);
    return store.reviewRefund(id,true,reviewer);
  }
  public Map<String,Object> approveRefund(String id, String permissions, String reviewer) { if (!authorization.canReviewRefund(permissions)) throw new SecurityException("forbidden"); return store.reviewRefund(id,true,reviewer); }

  @PostMapping("/admin/refunds/{id}/reject")
  public Map<String,Object> rejectRefund(@PathVariable("id") String id, @RequestHeader(value="X-User-Permissions",defaultValue="") String permissions, @RequestHeader(value="X-User-Scopes",defaultValue="") String scopes, @RequestHeader(value="X-User-Id",defaultValue="") String reviewer) {
    requireRefundScope(id, permissions, scopes);
    return store.reviewRefund(id,false,reviewer);
  }
  public Map<String,Object> rejectRefund(String id, String permissions, String reviewer) { if (!authorization.canReviewRefund(permissions)) throw new SecurityException("forbidden"); return store.reviewRefund(id,false,reviewer); }

  private void requireRefundScope(String id, String permissions, String scopes) {
    if (!authorization.canReviewRefund(permissions)) throw new SecurityException("forbidden");
    var request = store.refund(id).orElseThrow(() -> new java.util.NoSuchElementException("refund request not found"));
    var order = store.find(String.valueOf(request.get("orderId"))).orElseThrow(() -> new java.util.NoSuchElementException("order not found"));
    if (!authorization.canReviewRefund(permissions, scopes, String.valueOf(order.get("sessionId")))) throw new SecurityException("forbidden");
  }
}
