package com.cloudticket.inventory.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.inventory.persistence.entity.InventorySeatEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface InventorySeatMapper extends BaseMapper<InventorySeatEntity> {

  @Select("SELECT DISTINCT session_id FROM inventory_seat ORDER BY session_id")
  List<String> selectSessionIds();

  /**
   * Listing for an operator limited to session or activity scopes.
   *
   * <p>The scope predicate runs in the database so the page count matches what the operator may see.
   */
  @Select("SELECT * FROM inventory_seat WHERE (#{sessionId} = '' OR session_id = #{sessionId}) "
      + "AND (#{status} = '' OR status = #{status}) "
      + "AND (FIND_IN_SET(CONCAT('SESSION:', session_id), REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET(CONCAT('ACTIVITY:', activity_id), REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET('SESSION:*', REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET('ACTIVITY:*', REPLACE(#{scopes}, ' ', '')) > 0) "
      + "ORDER BY session_id,seat_index,row_label,seat_number")
  Page<InventorySeatEntity> selectScopedPage(Page<InventorySeatEntity> page,
                                             @Param("sessionId") String sessionId,
                                             @Param("status") String status,
                                             @Param("scopes") String scopes);
}
