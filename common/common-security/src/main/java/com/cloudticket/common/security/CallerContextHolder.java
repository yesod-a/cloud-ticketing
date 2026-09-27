package com.cloudticket.common.security;

/** Per-request holder for the {@link CallerContext} the servlet filter resolves from headers. */
public final class CallerContextHolder {

  private static final ThreadLocal<CallerContext> CURRENT = new ThreadLocal<>();

  private CallerContextHolder() {}

  public static CallerContext current() {
    CallerContext context = CURRENT.get();
    return context == null ? CallerContext.ANONYMOUS : context;
  }

  public static void set(CallerContext context) {
    if (context == null) CURRENT.remove();
    else CURRENT.set(context);
  }

  public static void clear() {
    CURRENT.remove();
  }

  /** Runs the given action with a fixed caller, restoring the previous one afterwards. */
  public static <T> T scoped(CallerContext context, java.util.function.Supplier<T> action) {
    CallerContext previous = CURRENT.get();
    set(context);
    try {
      return action.get();
    } finally {
      set(previous);
    }
  }
}
