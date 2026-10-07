package com.cloudticket.promotion.domain;
import org.springframework.stereotype.Component;
@Component("NO_THRESHOLD") public class NoThresholdDiscount implements DiscountStrategy { public boolean canUse(int amount,Coupon c){return c.discountValue()>0;} public int calculateDiscount(int amount,Coupon c){return Math.min(amount,Math.max(0,c.discountValue()));} public String describe(Coupon c){return "立减"+c.discountValue();} }
