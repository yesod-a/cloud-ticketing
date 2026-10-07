package com.cloudticket.comment;
import org.mybatis.spring.annotation.MapperScan; import org.springframework.boot.SpringApplication; import org.springframework.boot.autoconfigure.SpringBootApplication; import org.springframework.scheduling.annotation.EnableScheduling;
@SpringBootApplication @EnableScheduling @MapperScan("com.cloudticket.comment.persistence.mapper") public class CommentApplication { public static void main(String[] args){SpringApplication.run(CommentApplication.class,args);} }
