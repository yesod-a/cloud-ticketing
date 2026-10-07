package com.cloudticket.promotion.persistence.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper; import com.cloudticket.promotion.persistence.entity.CouponEntity; import org.apache.ibatis.annotations.Select; import java.time.Instant; import java.util.List;
public interface CouponMapper extends BaseMapper<CouponEntity> { @Select("SELECT * FROM promotion_coupon WHERE status='PUBLISHED' AND issue_begin_at<=#{now} AND issue_end_at>#{now} ORDER BY created_at DESC") List<CouponEntity> selectAvailable(Instant now); }
