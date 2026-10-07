package com.cloudticket.inventory.reconcile;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class InventoryCachePreheatConfiguration {
  @Bean(name = "inventoryCachePreheatExecutor")
  Executor inventoryCachePreheatExecutor(
      @Value("${cloudticket.inventory.preheat.pool-size:4}") int poolSize,
      @Value("${cloudticket.inventory.preheat.queue-capacity:100}") int queueCapacity) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    int size = Math.max(1, poolSize);
    executor.setCorePoolSize(size);
    executor.setMaxPoolSize(size);
    executor.setQueueCapacity(Math.max(1, queueCapacity));
    executor.setThreadNamePrefix("inventory-preheat-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }

  @Bean
  @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${xxl.job.admin.addresses:}')")
  XxlJobSpringExecutor xxlJobExecutor(
      @Value("${xxl.job.admin.addresses}") String adminAddresses,
      @Value("${xxl.job.executor.appname:inventory-service}") String appName,
      @Value("${xxl.job.executor.address:}") String address,
      @Value("${xxl.job.executor.ip:}") String ip,
      @Value("${xxl.job.executor.port:0}") int port,
      @Value("${xxl.job.accessToken:default_token}") String accessToken,
      @Value("${xxl.job.executor.logpath:./logs/xxl-job}") String logPath,
      @Value("${xxl.job.executor.logretentiondays:30}") int logRetentionDays) {
    XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
    executor.setAdminAddresses(adminAddresses);
    executor.setAppname(appName);
    executor.setAddress(address);
    executor.setIp(ip);
    executor.setPort(port);
    executor.setAccessToken(accessToken);
    executor.setLogPath(logPath);
    executor.setLogRetentionDays(logRetentionDays);
    return executor;
  }
}
