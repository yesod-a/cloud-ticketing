package com.cloudticket.order.api;

import com.cloudticket.common.security.CallerContext;
import com.cloudticket.common.security.CallerContextHolder;
import com.cloudticket.common.security.RequirePermission;
import com.cloudticket.common.security.RequireScope;
import com.cloudticket.order.PaymentService;
import com.cloudticket.order.persistence.OrderRepository;
import com.cloudticket.order.persistence.RefundRepository;
import com.cloudticket.order.security.OrderAuthorization;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  private final OrderRepository orders;
  private final RefundRepository refunds;
  private final PaymentService payments;
  private final OrderAuthorization authorization = new OrderAuthorization();

  public OrderController(OrderRepository orders, RefundRepository refunds, PaymentService payments) {
    this.orders = orders;
    this.refunds = refunds;
    this.payments = payments;
  }

  @PostMapping
  public Map<String, Object> create(@RequestHeader("X-User-Id") String user, @RequestBody Map<String, Object> body) {
    if (body != null && body.get("quantity") != null) {
      if (hasText(body, "seatIds")) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "quantity and seatIds cannot be sent together");
      }
      int quantity = ((Number) body.get("quantity")).intValue();
      String couponId = text(body, "couponId");
      return OrderViews.order(couponId.isBlank()
          ? orders.createGeneralAdmission(user, text(body, "sessionId"), quantity, text(body, "idempotencyKey"))
          : orders.createGeneralAdmission(user, text(body, "sessionId"), quantity, text(body, "idempotencyKey"), couponId));
    }
    String couponId = text(body, "couponId");
    return OrderViews.order(couponId.isBlank()
        ? orders.create(user, text(body, "sessionId"), text(body, "seatIds"), text(body, "idempotencyKey"))
        : orders.create(user, text(body, "sessionId"), text(body, "seatIds"), text(body, "idempotencyKey"), couponId));
  }

  @GetMapping("/me")
  public Map<String, Object> mine(@RequestHeader("X-User-Id") String user,
                                  @RequestParam(name = "page", defaultValue = "0") int page,
                                  @RequestParam(name = "size", defaultValue = "20") int size) {
    return OrderViews.ordersPage(orders.pageForUser(user, page, size));
  }

  @RequirePermission("order:read")
  @GetMapping("/admin")
  public Map<String, Object> admin(@RequestParam(name = "status", defaultValue = "") String status,
                                   @RequestParam(name = "page", defaultValue = "0") int page,
                                   @RequestParam(name = "size", defaultValue = "20") int size) {
    CallerContext caller = CallerContextHolder.current();
    return OrderViews.ordersPage(orders.pageForAdmin(status, page, size, caller.permissions(), caller.scopes()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable("id") String id, @RequestHeader("X-User-Id") String user) {
    CallerContext caller = CallerContextHolder.current();
    return orders.find(id)
        .filter(order -> authorization.canRead(user, order.getUserId(), caller.permissions(), caller.scopes(),
            "SESSION", order.getSessionId()))
        .map(order -> ResponseEntity.ok(OrderViews.order(order)))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<?> cancel(@PathVariable("id") String id, @RequestHeader("X-User-Id") String user) {
    CallerContext caller = CallerContextHolder.current();
    return orders.find(id)
        .filter(order -> authorization.canWrite(user, order.getUserId(), caller.permissions(), caller.scopes(),
            "SESSION", order.getSessionId()))
        .map(order -> ResponseEntity.ok(OrderViews.order(orders.cancel(id))))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @PostMapping("/{id}/refund")
  public ResponseEntity<?> requestRefund(@PathVariable("id") String id, @RequestHeader("X-User-Id") String user,
                                         @RequestBody Map<String, String> body) {
    return orders.find(id)
        .filter(order -> authorization.canRequestRefund(user, order.getUserId()))
        .map(order -> ResponseEntity.ok(OrderViews.refund(refunds.request(id, user, body.get("reason")))))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/{id}/payment")
  public Map<String, Object> payment(@PathVariable("id") String id, @RequestHeader("X-User-Id") String user) {
    return payments.status(id, user);
  }

  @PostMapping("/{id}/payments")
  public Map<String, Object> createPayment(@PathVariable("id") String id, @RequestHeader("X-User-Id") String user,
                                           @RequestBody(required = false) Map<String, Object> body) {
    String method = body == null ? null : String.valueOf(body.getOrDefault("method", ""));
    return payments.intent(id, user, method);
  }

  @PostMapping("/{id}/pay")
  public Map<String, Object> pay(@PathVariable("id") String id, @RequestHeader("X-User-Id") String user) {
    return payments.pay(id, user);
  }

  @RequirePermission("order:refund")
  @GetMapping("/admin/refunds")
  public Map<String, Object> refunds(@RequestParam(name = "status", defaultValue = "") String status,
                                     @RequestParam(name = "page", defaultValue = "0") int page,
                                     @RequestParam(name = "size", defaultValue = "20") int size) {
    CallerContext caller = CallerContextHolder.current();
    return OrderViews.refundsPage(refunds.page(status, page, size, caller.permissions(), caller.scopes()));
  }

  @RequirePermission("order:refund")
  @RequireScope(type = "SESSION", id = "@refundScopes.session(#id)")
  @PostMapping("/admin/refunds/{id}/approve")
  public Map<String, Object> approveRefund(@PathVariable("id") String id,
                                           @RequestHeader(value = "X-User-Id", defaultValue = "") String reviewer) {
    return OrderViews.refund(refunds.review(id, true, reviewer));
  }

  @RequirePermission("order:refund")
  @RequireScope(type = "SESSION", id = "@refundScopes.session(#id)")
  @PostMapping("/admin/refunds/{id}/reject")
  public Map<String, Object> rejectRefund(@PathVariable("id") String id,
                                          @RequestHeader(value = "X-User-Id", defaultValue = "") String reviewer) {
    return OrderViews.refund(refunds.review(id, false, reviewer));
  }

  private static String text(Map<String, Object> body, String key) {
    Object value = body == null ? null : body.get(key);
    return String.valueOf(value == null ? "" : value);
  }

  private static boolean hasText(Map<String, Object> body, String key) {
    Object value = body == null ? null : body.get(key);
    return value != null && !String.valueOf(value).isBlank();
  }
}
