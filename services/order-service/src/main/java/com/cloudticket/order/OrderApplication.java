package com.cloudticket.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableScheduling
@EnableKafka
@MapperScan("com.cloudticket.order.persistence.mapper")
public class OrderApplication {
  public static void main(String[] a) { SpringApplication.run(OrderApplication.class, a); }
}
