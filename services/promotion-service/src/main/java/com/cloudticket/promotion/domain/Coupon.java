package com.cloudticket.promotion.domain;

public record Coupon(String id, String name, String discountType, int thresholdAmountMinor, int discountValue,
                     Integer maxDiscountMinor, int totalCount, int issuedCount, int userLimit) {}
