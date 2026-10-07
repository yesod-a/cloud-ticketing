package com.cloudticket.promotion;
import static org.junit.jupiter.api.Assertions.*;
import com.cloudticket.promotion.domain.*;
import org.junit.jupiter.api.Test;
class DiscountStrategyTest {
  @Test void priceDiscountRequiresThresholdAndNeverExceedsAmount(){var c=new Coupon("1","满减","PRICE",1000,300,null,10,0,1);var s=new PriceDiscount();assertFalse(s.canUse(999,c));assertEquals(300,s.calculateDiscount(1200,c));assertEquals(100,s.calculateDiscount(100,c));}
  @Test void rateDiscountHonorsCap(){var c=new Coupon("1","折扣","RATE",1000,800,100,10,0,1);var s=new RateDiscount();assertEquals(100,s.calculateDiscount(2000,c));}
  @Test void noThresholdClampsToZero(){var c=new Coupon("1","立减","NO_THRESHOLD",0,500,null,10,0,1);assertEquals(200,new NoThresholdDiscount().calculateDiscount(200,c));}
}
