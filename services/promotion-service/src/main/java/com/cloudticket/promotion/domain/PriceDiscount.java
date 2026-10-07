package com.cloudticket.promotion.domain;
import org.springframework.stereotype.Component;
@Component("PRICE") public class PriceDiscount implements DiscountStrategy { public boolean canUse(int amount,Coupon c){return amount>=c.thresholdAmountMinor()&&c.discountValue()>0;} public int calculateDiscount(int amount,Coupon c){return Math.min(amount,Math.max(0,c.discountValue()));} public String describe(Coupon c){return "满"+c.thresholdAmountMinor()+"减"+c.discountValue();} }
