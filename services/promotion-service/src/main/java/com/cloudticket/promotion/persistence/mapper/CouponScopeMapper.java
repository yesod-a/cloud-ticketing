package com.cloudticket.promotion.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.cloudticket.promotion.persistence.entity.CouponScopeEntity; import org.apache.ibatis.annotations.Select; import java.util.List;
public interface CouponScopeMapper extends BaseMapper<CouponScopeEntity> { @Select("SELECT * FROM promotion_coupon_scope WHERE coupon_id=#{couponId}") List<CouponScopeEntity> selectByCoupon(String couponId); }
