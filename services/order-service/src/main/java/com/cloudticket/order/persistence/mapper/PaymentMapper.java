package com.cloudticket.order.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudticket.order.persistence.entity.PaymentEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface PaymentMapper extends BaseMapper<PaymentEntity> {

  /** The payment may only succeed while it is still pending; the database decides the winner. */
  @Update("UPDATE payment SET status='SUCCESS', paid_at=CURRENT_TIMESTAMP, provider_transaction_id=#{providerTransactionId} "
      + "WHERE id=#{paymentId} AND status='PENDING'")
  int markPaid(@Param("paymentId") String paymentId, @Param("providerTransactionId") String providerTransactionId);

  @Update("UPDATE payment SET method=#{method}, qr_token=#{qrToken} WHERE order_id=#{orderId} AND status='PENDING'")
  int refreshIntent(@Param("orderId") String orderId, @Param("method") String method,
                    @Param("qrToken") String qrToken);
}
