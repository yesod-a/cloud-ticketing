package com.cloudticket.promotion;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.cloudticket.promotion.persistence.mapper")
public class PromotionApplication {
  public static void main(String[] args) {
    SpringApplication.run(PromotionApplication.class, args);
  }
}
