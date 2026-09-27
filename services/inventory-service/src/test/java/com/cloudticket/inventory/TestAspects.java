package com.cloudticket.inventory;

import com.cloudticket.common.security.AuditSink;
import com.cloudticket.common.security.AuthorizationAspect;
import java.util.Map;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;

/** Wraps a controller in the real {@link AuthorizationAspect} so tests exercise the annotations. */
final class TestAspects {

  private TestAspects() {}

  static <T> T authorized(T target, AuditSink sink, Map<String, Object> lookupBeans) {
    GenericApplicationContext context = new GenericApplicationContext();
    context.refresh();
    lookupBeans.forEach((name, bean) -> context.getBeanFactory().registerSingleton(name, bean));

    StaticListableBeanFactory providers = new StaticListableBeanFactory();
    if (sink != null) providers.addBean("auditSink", sink);

    AspectJProxyFactory factory = new AspectJProxyFactory(target);
    factory.addAspect(new AuthorizationAspect("dev-internal-token",
        providers.getBeanProvider(AuditSink.class), context));
    return factory.getProxy();
  }
}
