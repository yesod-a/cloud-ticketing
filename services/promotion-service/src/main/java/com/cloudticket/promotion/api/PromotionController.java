package com.cloudticket.promotion.api;
import com.cloudticket.common.web.PageResult; import com.cloudticket.promotion.persistence.entity.CouponEntity; import com.cloudticket.promotion.service.CouponService; import java.util.Map; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/promotions/coupons") public class PromotionController { private final CouponService service; public PromotionController(CouponService service){this.service=service;}
 @GetMapping("/available") public Map<String,Object> available(@RequestHeader("X-User-Id") String user,@RequestParam String activityId,@RequestParam String sessionId){return Map.of("items",service.available(user,activityId,sessionId));}
 @PostMapping("/{couponId}/claim") public Map<String,Object> claim(@RequestHeader("X-User-Id") String user,@PathVariable String couponId){var row=service.claim(user,couponId);return Map.of("id",row.getId(),"couponId",row.getCouponId(),"status",row.getStatus());}
 @GetMapping("/my") public Map<String,Object> mine(@RequestHeader("X-User-Id") String user,@RequestParam(defaultValue="") String status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.mine(user,status,page,size).asMap();}
}
