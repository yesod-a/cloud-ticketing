package com.cloudticket.promotion.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

@TableName("promotion_coupon")
public class CouponEntity {
  @TableId(value = "id", type = IdType.INPUT) private String id;
  private String name; private String discountType; private Integer thresholdAmountMinor; private Integer discountValue;
  private Integer maxDiscountMinor; private Integer totalCount; private Integer issuedCount; private Integer usedCount;
  private Integer userLimit; private Instant issueBeginAt; private Instant issueEndAt; private Instant termBeginAt; private Instant termEndAt;
  private Integer termDays; private String status; private String createdBy; private Instant createdAt; private Instant updatedAt;
  public String getId(){return id;} public void setId(String v){id=v;} public String getName(){return name;} public void setName(String v){name=v;}
  public String getDiscountType(){return discountType;} public void setDiscountType(String v){discountType=v;} public Integer getThresholdAmountMinor(){return thresholdAmountMinor;} public void setThresholdAmountMinor(Integer v){thresholdAmountMinor=v;}
  public Integer getDiscountValue(){return discountValue;} public void setDiscountValue(Integer v){discountValue=v;} public Integer getMaxDiscountMinor(){return maxDiscountMinor;} public void setMaxDiscountMinor(Integer v){maxDiscountMinor=v;}
  public Integer getTotalCount(){return totalCount;} public void setTotalCount(Integer v){totalCount=v;} public Integer getIssuedCount(){return issuedCount;} public void setIssuedCount(Integer v){issuedCount=v;}
  public Integer getUsedCount(){return usedCount;} public void setUsedCount(Integer v){usedCount=v;} public Integer getUserLimit(){return userLimit;} public void setUserLimit(Integer v){userLimit=v;}
  public Instant getIssueBeginAt(){return issueBeginAt;} public void setIssueBeginAt(Instant v){issueBeginAt=v;} public Instant getIssueEndAt(){return issueEndAt;} public void setIssueEndAt(Instant v){issueEndAt=v;}
  public Instant getTermBeginAt(){return termBeginAt;} public void setTermBeginAt(Instant v){termBeginAt=v;} public Instant getTermEndAt(){return termEndAt;} public void setTermEndAt(Instant v){termEndAt=v;}
  public Integer getTermDays(){return termDays;} public void setTermDays(Integer v){termDays=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;}
  public String getCreatedBy(){return createdBy;} public void setCreatedBy(String v){createdBy=v;} public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;} public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
