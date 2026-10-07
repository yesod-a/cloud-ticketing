package com.cloudticket.promotion.api;
import com.cloudticket.common.security.RequireInternalToken; import com.cloudticket.promotion.service.CouponReservationService; import java.util.Map; import org.springframework.web.bind.annotation.*;
@RestController @RequireInternalToken @RequestMapping("/api/internal/promotions") public class InternalPromotionController { private final CouponReservationService reservations; public InternalPromotionController(CouponReservationService r){reservations=r;}
 @PostMapping("/quote") public Map<String,Object> quote(@RequestBody Map<String,Object> b){var q=reservations.quote(text(b,"orderId"),text(b,"userId"),text(b,"activityId"),text(b,"sessionId"),((Number)b.getOrDefault("originalAmountMinor",0)).intValue(),text(b,"couponId"));return Map.of("reservationId",q.reservationId()==null?"":q.reservationId(),"discountAmountMinor",q.discountAmountMinor(),"payableAmountMinor",q.payableAmountMinor());}
 @PostMapping("/{id}/{action}") public Map<String,Object> transition(@PathVariable String id,@PathVariable String action){reservations.transition(id,action);return Map.of("status","OK","reservationId",id);}
 private static String text(Map<String,Object> b,String k){Object v=b.get(k);return v==null?"":String.valueOf(v);}
}
