package com.cloudticket.promotion.domain;
public interface DiscountStrategy { boolean canUse(int originalAmountMinor, Coupon coupon); int calculateDiscount(int originalAmountMinor, Coupon coupon); String describe(Coupon coupon); }
