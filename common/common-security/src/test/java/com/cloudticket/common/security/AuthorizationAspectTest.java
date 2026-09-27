package com.cloudticket.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies the aspect through the real auto-configuration rather than a hand-built proxy: the point
 * is that a service gets the enforcement simply by having {@code common-security} on its classpath.
 */
class AuthorizationAspectTest {

  private final List<AuditEntry> recorded = new ArrayList<>();

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, SecurityAutoConfiguration.class))
      .withBean("auditSink", AuditSink.class, () -> recorded::add)
      .withBean("snapshots", Snapshots.class)
      .withBean(GuardedEndpoints.class)
      .withBean(InternalEndpoints.class);

  @Test
  void applyingTheAutoConfigurationEnforcesPermissions() {
    runner.run(context -> {
      GuardedEndpoints endpoints = context.getBean(GuardedEndpoints.class);

      assertEquals("read:a1", asCaller("activity:read", "ACTIVITY:a1", "", () -> endpoints.read("a1")));
      assertThrows(SecurityException.class, () -> asCaller("order:read", "ACTIVITY:a1", "",
          () -> endpoints.read("a1")));
    });
  }

  @Test
  void systemConfigSatisfiesAnyPermissionRequirement() {
    runner.run(context -> assertEquals("read:a1", asCaller("system:config", "", "",
        () -> context.getBean(GuardedEndpoints.class).read("a1"))));
  }

  @Test
  void anyOfTheListedPermissionsIsEnough() {
    runner.run(context -> {
      GuardedEndpoints endpoints = context.getBean(GuardedEndpoints.class);

      assertEquals("write", asCaller("a:write", "", "", () -> endpoints.write()));
      assertEquals("write", asCaller("b:write", "", "", () -> endpoints.write()));
      assertThrows(SecurityException.class, () -> asCaller("c:write", "", "", endpoints::write));
    });
  }

  @Test
  void resourceScopeIsCheckedAgainstTheMethodParameter() {
    runner.run(context -> {
      GuardedEndpoints endpoints = context.getBean(GuardedEndpoints.class);

      assertEquals("read:a1", asCaller("activity:read", "ACTIVITY:a1", "", () -> endpoints.read("a1")));
      assertThrows(SecurityException.class, () -> asCaller("activity:read", "ACTIVITY:a2", "",
          () -> endpoints.read("a1")));
    });
  }

  @Test
  void resourceScopeCanBeResolvedThroughALookupBean() {
    runner.run(context -> {
      GuardedEndpoints endpoints = context.getBean(GuardedEndpoints.class);

      assertEquals("seat", asCaller("seat:write", "SESSION:s1", "", () -> endpoints.moveSeat("seat-1")));
      assertThrows(SecurityException.class, () -> asCaller("seat:write", "SESSION:s9", "",
          () -> endpoints.moveSeat("seat-1")));
    });
  }

  @Test
  void internalTokenIsRequiredOnAnnotatedMethods() {
    runner.run(context -> {
      GuardedEndpoints endpoints = context.getBean(GuardedEndpoints.class);

      assertEquals("ok", asCaller("", "", "dev-internal-token", endpoints::internal));
      assertThrows(SecurityException.class, () -> asCaller("", "", "wrong", endpoints::internal));
    });
  }

  @Test
  void internalTokenCanBeDeclaredOnceForTheWholeController() {
    runner.run(context -> {
      InternalEndpoints endpoints = context.getBean(InternalEndpoints.class);

      assertEquals("ok", asCaller("", "", "dev-internal-token", endpoints::ping));
      assertThrows(SecurityException.class, () -> asCaller("", "", "", endpoints::ping));
    });
  }

  @Test
  void auditCapturesBeforeAndAfterAndTheOperatorReason() {
    runner.run(context -> {
      GuardedEndpoints endpoints = context.getBean(GuardedEndpoints.class);

      asCaller("activity:write", "ACTIVITY:a1", "",
          () -> endpoints.rename("a1", new Rename("New", "because")));

      AuditEntry entry = recorded.get(recorded.size() - 1);
      assertEquals("actor-1", entry.actor());
      assertEquals("ACTIVITY_RENAMED", entry.action());
      assertEquals("ACTIVITY", entry.resourceType());
      assertEquals("a1", entry.resourceId());
      assertEquals("Activity[a1=Old]", entry.before());
      assertEquals("Activity[a1=New]", entry.after());
      assertEquals("because", entry.reason());
      assertEquals("trace-1", entry.traceId());
    });
  }

  @Test
  void auditReadsTheResourceIdFromTheReturnValue() {
    runner.run(context -> {
      GuardedEndpoints endpoints = context.getBean(GuardedEndpoints.class);

      asCaller("activity:write", "", "", () -> endpoints.create(new Rename("New", "first")));

      assertEquals("generated-id", recorded.get(recorded.size() - 1).resourceId());
    });
  }

  @Test
  void auditIsSkippedWhenTheOperationChangedNothing() {
    runner.run(context -> {
      GuardedEndpoints endpoints = context.getBean(GuardedEndpoints.class);

      asCaller("activity:write", "", "", () -> endpoints.idempotent(new Rename("New", "")));

      assertTrue(recorded.isEmpty(), "an idempotent call must not add an audit line");
    });
  }

  @Test
  void auditIsNotWrittenWhenTheUseCaseFails() {
    runner.run(context -> {
      GuardedEndpoints endpoints = context.getBean(GuardedEndpoints.class);

      assertThrows(IllegalStateException.class,
          () -> asCaller("activity:write", "", "", endpoints::fail));
      assertTrue(recorded.isEmpty());
    });
  }

  @Test
  void auditIsSkippedWhenTheServiceRegistersNoSink() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AopAutoConfiguration.class, SecurityAutoConfiguration.class))
        .withBean("snapshots", Snapshots.class)
        .withBean(GuardedEndpoints.class)
        .run(context -> assertEquals("read:a1", asCaller("activity:read", "ACTIVITY:a1", "",
            () -> context.getBean(GuardedEndpoints.class).read("a1"))));
  }

  private static <T> T asCaller(String permissions, String scopes, String internalToken,
                                Supplier<T> action) {
    return CallerContextHolder.scoped(
        new CallerContext(permissions, scopes, "actor-1", "trace-1", internalToken), action);
  }

  public record Rename(String to, String reason) {}

  public record Activity(String id, String title) {
    @Override
    public String toString() {
      return "Activity[" + id + "=" + title + "]";
    }
  }

  /** Fixture endpoints exercising every annotation. */
  public static class GuardedEndpoints {

    @RequirePermission("activity:read")
    @RequireScope(type = "ACTIVITY", id = "#id")
    public String read(String id) {
      return "read:" + id;
    }

    @RequirePermission({"a:write", "b:write"})
    public String write() {
      return "write";
    }

    @RequirePermission("seat:write")
    @RequireScope(type = "SESSION", id = "@snapshots.session(#id)",
        parent = "@snapshots.activityOfSeat(#id)")
    public String moveSeat(String id) {
      return "seat";
    }

    @RequireInternalToken
    public String internal() {
      return "ok";
    }

    @RequirePermission("activity:write")
    @RequireScope(type = "ACTIVITY", id = "#id")
    @AuditAction(action = "ACTIVITY_RENAMED", resourceType = "ACTIVITY", resourceId = "#id",
        before = "@snapshots.describe(#id)", after = "#result", reason = "#body.reason()")
    public Activity rename(String id, Rename body) {
      return new Activity(id, body.to());
    }

    @RequirePermission("activity:write")
    @AuditAction(action = "ACTIVITY_CREATED", resourceType = "ACTIVITY", resourceId = "#result.id()",
        before = "", after = "#result")
    public Activity create(Rename body) {
      return new Activity("generated-id", body.to());
    }

    @RequirePermission("activity:write")
    @AuditAction(action = "ACTIVITY_TOUCHED", resourceType = "ACTIVITY", resourceId = "#body.to()",
        before = "", after = "#result", when = "#result.id() != 'generated-id'")
    public Activity idempotent(Rename body) {
      return new Activity("generated-id", body.to());
    }

    @RequirePermission("activity:write")
    @AuditAction(action = "ACTIVITY_FAILED", resourceType = "ACTIVITY", resourceId = "#body.to()")
    public Activity fail() {
      throw new IllegalStateException("boom");
    }
  }

  /** Fixture with the internal-token requirement declared once for the class. */
  @RequireInternalToken
  public static class InternalEndpoints {
    public String ping() {
      return "ok";
    }
  }

  /** Lookup bean the scope and audit expressions call. */
  public static class Snapshots {
    public String session(String seatId) {
      return "seat-1".equals(seatId) ? "s1" : null;
    }

    public String activityOfSeat(String seatId) {
      return "seat-1".equals(seatId) ? "a1" : null;
    }

    public String describe(String activityId) {
      return "Activity[" + activityId + "=Old]";
    }
  }
}
