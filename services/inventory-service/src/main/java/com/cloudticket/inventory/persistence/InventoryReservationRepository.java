package com.cloudticket.inventory.persistence;
import com.cloudticket.inventory.persistence.entity.InventoryReservationEntity;
import com.cloudticket.inventory.persistence.mapper.InventoryReservationMapper;
import java.util.Optional;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
@Repository public class InventoryReservationRepository {
  private final InventoryReservationMapper rows; public InventoryReservationRepository(InventoryReservationMapper rows){this.rows=rows;}
  public Optional<InventoryReservationEntity> find(String id){return Optional.ofNullable(rows.selectById(id));}
  public boolean insertIfAbsent(InventoryReservationEntity row){try{return rows.insert(row)>0;}catch(DuplicateKeyException e){return false;}}
  public int transition(String id,String from,String to,String error,java.time.Instant next){return rows.transition(id,from,to,error,next);}
  public int updateSeatMapping(String id, String seatIds, String seatIndexes) { return rows.updateSeatMapping(id, seatIds, seatIndexes); }
  public List<InventoryReservationEntity> expiredHeld(java.time.Instant now, int limit) { return rows.expiredHeld(now, limit); }
}
