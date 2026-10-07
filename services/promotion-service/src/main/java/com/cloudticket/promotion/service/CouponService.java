package com.cloudticket.promotion.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudticket.common.web.PageResult;
import com.cloudticket.promotion.domain.Coupon;
import com.cloudticket.promotion.domain.DiscountStrategy;
import com.cloudticket.promotion.persistence.entity.CouponEntity;
import com.cloudticket.promotion.persistence.entity.CouponScopeEntity;
import com.cloudticket.promotion.persistence.entity.UserCouponEntity;
import com.cloudticket.promotion.persistence.mapper.CouponMapper;
import com.cloudticket.promotion.persistence.mapper.CouponScopeMapper;
import com.cloudticket.promotion.persistence.mapper.UserCouponMapper;
import com.cloudticket.promotion.redis.CouponClaimService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {
  private final CouponMapper coupons; private final CouponScopeMapper scopes; private final UserCouponMapper userCoupons; private final CouponClaimService claimService; private final Map<String,DiscountStrategy> strategies;
  public CouponService(CouponMapper coupons,CouponScopeMapper scopes,UserCouponMapper userCoupons,CouponClaimService claimService,List<DiscountStrategy> list){this.coupons=coupons;this.scopes=scopes;this.userCoupons=userCoupons;this.claimService=claimService;this.strategies=list.stream().collect(Collectors.toMap(x->x.getClass().getAnnotation(org.springframework.stereotype.Component.class).value(),Function.identity(),(a,b)->a));}
  public List<CouponEntity> available(String userId,String activityId,String sessionId){return coupons.selectAvailable(Instant.now()).stream().filter(c->scopes.selectByCoupon(c.getId()).stream().anyMatch(s->matches(s,activityId,sessionId))||scopes.selectByCoupon(c.getId()).isEmpty()).toList();}
  @Transactional public UserCouponEntity claim(String userId,String couponId){CouponEntity c=coupons.selectById(couponId);if(c==null||!"PUBLISHED".equals(c.getStatus()))throw new IllegalArgumentException("coupon unavailable");Instant now=Instant.now();if(now.isBefore(c.getIssueBeginAt())||!now.isBefore(c.getIssueEndAt()))throw new IllegalArgumentException("coupon issue window closed");if(c.getIssuedCount()>=c.getTotalCount())throw new IllegalStateException("coupon sold out");UserCouponEntity existing=userCoupons.selectOne(Wrappers.<UserCouponEntity>lambdaQuery().eq(UserCouponEntity::getCouponId,couponId).eq(UserCouponEntity::getUserId,userId));if(existing!=null) return existing;claimService.claim(c,userId);UserCouponEntity row=new UserCouponEntity();row.setId(UUID.randomUUID().toString());row.setCouponId(couponId);row.setUserId(userId);row.setStatus("UNUSED");row.setTermBeginAt(c.getTermBeginAt()==null?now:c.getTermBeginAt());row.setTermEndAt(c.getTermEndAt());try{userCoupons.insert(row);coupons.update(Wrappers.<CouponEntity>lambdaUpdate().eq(CouponEntity::getId,couponId).setSql("issued_count=issued_count+1"));return row;}catch(DuplicateKeyException e){return userCoupons.selectOne(Wrappers.<UserCouponEntity>lambdaQuery().eq(UserCouponEntity::getCouponId,couponId).eq(UserCouponEntity::getUserId,userId));}}
  public PageResult<UserCouponEntity> mine(String userId,String status,int page,int size){var q=Wrappers.<UserCouponEntity>lambdaQuery().eq(UserCouponEntity::getUserId,userId).eq(status!=null&&!status.isBlank(),UserCouponEntity::getStatus,status).orderByDesc(UserCouponEntity::getCreatedAt);var p=couponsPage(q,page,size);return p;}
  private PageResult<UserCouponEntity> couponsPage(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserCouponEntity> q,int page,int size){int p=PageResult.safePage(page),s=PageResult.safeSize(size);var result=userCoupons.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(p+1L,s),q);return new PageResult<>(result.getRecords(),p,s,result.getTotal());}
  public Coupon toDomain(CouponEntity c){return new Coupon(c.getId(),c.getName(),c.getDiscountType(),nz(c.getThresholdAmountMinor()),nz(c.getDiscountValue()),c.getMaxDiscountMinor(),nz(c.getTotalCount()),nz(c.getIssuedCount()),nz(c.getUserLimit()));}
  public DiscountStrategy strategy(Coupon c){DiscountStrategy s=strategies.get(c.discountType());if(s==null)throw new IllegalArgumentException("unsupported discount type");return s;}
  private static boolean matches(CouponScopeEntity s,String activity,String session){return "ALL".equalsIgnoreCase(s.getResourceType())||("ACTIVITY".equalsIgnoreCase(s.getResourceType())&&s.getResourceId().equals(activity))||("SESSION".equalsIgnoreCase(s.getResourceType())&&s.getResourceId().equals(session));}
  private static int nz(Integer x){return x==null?0:x;}
}
