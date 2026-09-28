package com.cloudticket.activity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.activity.persistence.entity.ActivityEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface ActivityMapper extends BaseMapper<ActivityEntity> {

  /** An activity is publicly visible only while it has at least one on-sale session. */
  @Select("SELECT DISTINCT a.id,a.title,a.organizer,a.description,a.status,a.layout_frozen,a.created_at FROM activity a "
      + "JOIN activity_session s ON s.activity_id = a.id WHERE s.status = 'ONSALE' ORDER BY a.title")
  List<ActivityEntity> selectPublic();

  @Select("SELECT a.id,a.title,a.organizer,a.description,a.status,a.layout_frozen,a.created_at FROM activity a "
      + "JOIN activity_session s ON s.activity_id = a.id WHERE s.status = 'ONSALE' "
      + "AND (#{keyword} = '' OR a.title LIKE CONCAT('%', #{keyword}, '%')) "
      + "AND (#{organizer} = '' OR a.organizer LIKE CONCAT('%', #{organizer}, '%')) "
      + "GROUP BY a.id,a.title,a.organizer,a.status,a.layout_frozen,a.created_at ORDER BY a.title")
  Page<ActivityEntity> selectPublicPage(Page<ActivityEntity> page, @Param("keyword") String keyword,
                                        @Param("organizer") String organizer);

  /**
   * Administrative listing for an operator limited to resource scopes: the visibility rule runs in
   * the database so paging never counts rows the caller may not read.
   */
  @Select("SELECT a.id,a.title,a.organizer,a.description,a.status,a.layout_frozen,a.created_at FROM activity a "
      + "WHERE (#{keyword} = '' OR a.title LIKE CONCAT('%', #{keyword}, '%')) "
      + "AND (#{status} = '' OR a.status = #{status}) "
      + "AND (FIND_IN_SET(CONCAT('ACTIVITY:', a.id), REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET('ACTIVITY:*', REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR EXISTS (SELECT 1 FROM activity_session x WHERE x.activity_id = a.id "
      + "AND (FIND_IN_SET(CONCAT('SESSION:', x.id), REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET('SESSION:*', REPLACE(#{scopes}, ' ', '')) > 0)) "
      + "OR EXISTS (SELECT 1 FROM venue v WHERE v.activity_id = a.id "
      + "AND (FIND_IN_SET(CONCAT('VENUE:', v.id), REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET('VENUE:*', REPLACE(#{scopes}, ' ', '')) > 0))) "
      + "ORDER BY a.title")
  Page<ActivityEntity> selectVisibleToPage(Page<ActivityEntity> page, @Param("keyword") String keyword,
                                           @Param("status") String status, @Param("scopes") String scopes);

  /** Derives the activity status from its sessions instead of trusting a second write. */
  @Update("UPDATE activity SET status = CASE WHEN EXISTS (SELECT 1 FROM activity_session "
      + "WHERE activity_id = #{id} AND status = 'ONSALE') THEN 'PUBLISHED' ELSE 'OFFLINE' END WHERE id = #{id}")
  int refreshStatus(@Param("id") String id);
}
