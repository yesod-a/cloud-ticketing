package com.cloudticket.activity.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.activity.persistence.entity.SeatEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SeatMapper extends BaseMapper<SeatEntity> {

  /**
   * Snapshots the venue template into an independent set of session seats.
   *
   * <p>Runs as a single {@code INSERT ... SELECT} so a session never observes a half-copied layout.
   */
  @Insert("INSERT INTO seat (id,session_id,area_label,row_label,seat_number,display_name,seat_type,position_x,position_y,status) "
      + "SELECT UUID(),#{sessionId},area_label,row_label,seat_number,display_name,seat_type,position_x,position_y,status "
      + "FROM venue_seat WHERE venue_id = #{venueId}")
  int copyFromVenue(@Param("sessionId") String sessionId, @Param("venueId") String venueId);

  @Select("SELECT s.activity_id FROM seat x JOIN activity_session s ON s.id = x.session_id WHERE x.id = #{seatId}")
  List<String> selectActivityIdForSeat(@Param("seatId") String seatId);

  @Select("SELECT s.* FROM seat s JOIN activity_session x ON x.id = s.session_id "
      + "WHERE x.activity_id = #{activityId} ORDER BY x.starts_at,s.row_label,s.seat_number")
  List<SeatEntity> selectByActivity(@Param("activityId") String activityId);

  /**
   * Seats of every session of an activity, narrowed to the caller's session or activity scope.
   *
   * <p>The scope predicate runs in the database so the page count reflects what the caller may
   * actually see.
   */
  @Select("SELECT s.* FROM seat s JOIN activity_session x ON x.id = s.session_id "
      + "WHERE x.activity_id = #{activityId} "
      + "AND (FIND_IN_SET(CONCAT('ACTIVITY:', x.activity_id), REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET('ACTIVITY:*', REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET(CONCAT('SESSION:', s.session_id), REPLACE(#{scopes}, ' ', '')) > 0 "
      + "OR FIND_IN_SET('SESSION:*', REPLACE(#{scopes}, ' ', '')) > 0) "
      + "ORDER BY x.starts_at,s.row_label,s.seat_number")
  Page<SeatEntity> selectScopedByActivity(Page<SeatEntity> page, @Param("activityId") String activityId,
                                          @Param("scopes") String scopes);
}
