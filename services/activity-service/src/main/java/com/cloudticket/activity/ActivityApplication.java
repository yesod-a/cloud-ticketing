package com.cloudticket.activity;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication; import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication @EnableScheduling @MapperScan("com.cloudticket.activity.persistence.mapper")
public class ActivityApplication { public static void main(String[] args){SpringApplication.run(ActivityApplication.class,args);} }
