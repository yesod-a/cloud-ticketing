package com.cloudticket.order.timeout;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OrderTimeoutXxlConfiguration {
  @Bean
  @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${xxl.job.admin.addresses:}')")
  XxlJobSpringExecutor xxlJobExecutor(
      @Value("${xxl.job.admin.addresses}") String adminAddresses,
      @Value("${xxl.job.executor.appname:order-service}") String appName,
      @Value("${xxl.job.executor.address:}") String address,
      @Value("${xxl.job.executor.ip:}") String ip,
      @Value("${xxl.job.executor.port:0}") int port,
      @Value("${xxl.job.accessToken:default_token}") String accessToken,
      @Value("${xxl.job.executor.logpath:./logs/xxl-job}") String logPath,
      @Value("${xxl.job.executor.logretentiondays:30}") int retentionDays) {
    XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
    executor.setAdminAddresses(adminAddresses);
    executor.setAppname(appName);
    executor.setAddress(address);
    executor.setIp(ip);
    executor.setPort(port);
    executor.setAccessToken(accessToken);
    executor.setLogPath(logPath);
    executor.setLogRetentionDays(retentionDays);
    return executor;
  }
}
