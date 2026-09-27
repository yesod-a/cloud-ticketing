package com.cloudticket.activity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.activity.persistence.entity.SessionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SessionMapper extends BaseMapper<SessionEntity> {

  @Select("SELECT s.* FROM activity_session s JOIN venue v ON v.id = s.venue_id "
      + "WHERE s.activity_id = #{activityId} AND s.status = 'ONSALE' ORDER BY s.starts_at")
  List<SessionEntity> selectOnSale(@Param("activityId") String activityId);

  @Select("SELECT s.* FROM activity_session s WHERE s.id = #{id}")
  SessionEntity selectDetail(@Param("id") String id);

  /**
   * Listing for an operator limited to session or venue scopes.
   *
   * <p>An activity-level scope already grants access to the whole list and is handled before this
   * query runs, so only the narrower rules remain here.
   */
  @Select("SELECT s.* FROM activity_session s WHERE s.activity_id = #{activityId} "
      + "AND (#{status} = '' OR s.status = #{status}) "
      + "AND (FIND_IN_SET(CONCAT('SESSION:', s.id), REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET('SESSION:*', REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET(CONCAT('VENUE:', s.venue_id), REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET('VENUE:*', REPLACE(#{scopes}, ' ', '')) > 0) "
      + "ORDER BY s.starts_at")
  Page<SessionEntity> selectScopedPage(Page<SessionEntity> page, @Param("activityId") String activityId,
                                       @Param("status") String status, @Param("scopes") String scopes);
}
