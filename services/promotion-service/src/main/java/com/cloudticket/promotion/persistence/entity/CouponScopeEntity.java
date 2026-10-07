package com.cloudticket.promotion.persistence.entity;
import com.baomidou.mybatisplus.annotation.TableName;
@TableName("promotion_coupon_scope")
public class CouponScopeEntity { private String couponId; private String resourceType; private String resourceId; public String getCouponId(){return couponId;} public void setCouponId(String v){couponId=v;} public String getResourceType(){return resourceType;} public void setResourceType(String v){resourceType=v;} public String getResourceId(){return resourceId;} public void setResourceId(String v){resourceId=v;} }
