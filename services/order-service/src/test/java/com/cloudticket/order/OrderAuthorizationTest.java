package com.cloudticket.order;
import static org.junit.jupiter.api.Assertions.*;
import com.cloudticket.order.security.OrderAuthorization;
import org.junit.jupiter.api.Test;
class OrderAuthorizationTest {
  @Test void userCannotReadAnotherUsersOrder(){var p=new OrderAuthorization();assertTrue(p.canRead("a","a",""));assertFalse(p.canRead("a","b",""));assertTrue(p.canRead("a","b","order:read"));}
  @Test void refundRequestMayBeSubmittedByOwnerButApprovalRequiresPermission(){var p=new OrderAuthorization();assertTrue(p.canRequestRefund("a","a"));assertFalse(p.canRequestRefund("a","b"));assertTrue(p.canReviewRefund("order:refund"));assertFalse(p.canReviewRefund("order:read"));}
  @Test void refundApprovalRequiresMatchingSessionScopeUnlessSystemConfig(){var p=new OrderAuthorization();assertTrue(p.canReviewRefund("order:refund","SESSION:s1","s1"));assertFalse(p.canReviewRefund("order:refund","SESSION:s1","s2"));assertTrue(p.canReviewRefund("system:config","","s2"));}
}
