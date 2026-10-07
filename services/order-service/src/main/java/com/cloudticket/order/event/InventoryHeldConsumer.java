package com.cloudticket.order.event;

import com.cloudticket.common.events.EventTypes;
import com.cloudticket.order.persistence.OrderRepository;
import com.cloudticket.order.persistence.ProcessedEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Turns one durable InventoryHeld event into one pending order. */
@Component
@ConditionalOnProperty(prefix = "cloudticket.queued", name = "enabled", havingValue = "true")
public class InventoryHeldConsumer {
  private static final String CONSUMER = "order-queued-held-v1";
  private final OrderRepository orders; private final ProcessedEventRepository events; private final ObjectMapper json;
  public InventoryHeldConsumer(OrderRepository orders, ProcessedEventRepository events, ObjectMapper json){this.orders=orders;this.events=events;this.json=json;}

  @KafkaListener(topics="${cloudticket.queued.inventory-topic:inventory-events}", groupId="${cloudticket.queued.order-consumer-group:order-queued-v1}")
  public void consume(String raw) {
    try {
      JsonNode envelope=json.readTree(raw); if(!EventTypes.INVENTORY_HELD.equals(text(envelope,"eventType"))) return;
      JsonNode payload=envelope.path("payload"); String reservation=text(payload,"reservationId");
      if(reservation==null) throw new IllegalArgumentException("reservationId required");
      String user=text(payload,"userId"), session=text(payload,"sessionId");
      int quantity=payload.path("quantity").asInt(0); String seats=text(payload,"seatIds"); String numbers=text(payload,"ticketNumbers");
      orders.createQueuedHeld(reservation,user,session,seats,quantity,numbers);
      String eventId=text(envelope,"eventId");
      if(eventId!=null) events.tryClaim(eventId,CONSUMER,EventTypes.INVENTORY_HELD,reservation,sha256(raw),text(envelope,"traceId"));
    } catch (Exception failure) { throw new IllegalStateException("queued order creation failed", failure); }
  }
  private static String text(JsonNode n,String f){JsonNode v=n==null?null:n.get(f);return v==null||v.isNull()||v.asText().isBlank()?null:v.asText();}
  private static String sha256(String value)throws Exception{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder h=new StringBuilder();for(byte x:b)h.append(String.format("%02x",x));return h.toString();}
}
