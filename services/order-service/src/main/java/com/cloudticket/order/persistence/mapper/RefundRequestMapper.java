package com.cloudticket.order.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.order.persistence.entity.RefundRequestEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface RefundRequestMapper extends BaseMapper<RefundRequestEntity> {

  @Update("UPDATE refund_request SET status=#{status},reviewed_by=#{reviewedBy},reviewed_at=CURRENT_TIMESTAMP "
      + "WHERE id=#{id} AND status='REQUESTED'")
  int review(@Param("id") String id, @Param("status") String status, @Param("reviewedBy") String reviewedBy);

  /**
   * Scoped listing. The session filter runs in the database instead of loading every request into
   * memory, and the pagination interceptor adds the matching {@code COUNT} statement.
   */
  @Select("SELECT r.* FROM refund_request r JOIN ticket_order o ON o.id = r.order_id "
      + "WHERE (#{status} = '' OR r.status = #{status}) "
      + "AND (FIND_IN_SET(CONCAT('SESSION:', o.session_id), REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET('SESSION:*', REPLACE(#{scopes}, ' ', '')) > 0) "
      + "ORDER BY r.created_at DESC")
  Page<RefundRequestEntity> selectScopedPage(Page<RefundRequestEntity> page, @Param("status") String status,
                                             @Param("scopes") String scopes);
}
