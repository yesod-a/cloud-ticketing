package com.cloudticket.common.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pagination envelope returned by every admin listing endpoint.
 *
 * <p>Each service used to carry its own near-identical {@code Page} record plus a private {@code
 * page(...)} helper in the controller. One shared record keeps the JSON shape identical
 * ({@code items/page/size/total/totalPages}) everywhere.
 */
public record PageResult<T>(List<T> items, int page, int size, long total) {

  public PageResult {
    items = items == null ? List.of() : List.copyOf(items);
  }

  public long totalPages() {
    return total == 0 ? 0 : (total + size - 1) / size;
  }

  public Map<String, Object> asMap() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("items", items);
    value.put("page", page);
    value.put("size", size);
    value.put("total", total);
    value.put("totalPages", totalPages());
    return value;
  }

  /** Normalises a requested page number the way every listing endpoint does. */
  public static int safePage(int page) {
    return Math.max(0, page);
  }

  /** Normalises a requested page size to the shared 1..100 window. */
  public static int safeSize(int size) {
    return Math.min(100, Math.max(1, size));
  }

  public static <T> PageResult<T> slice(List<T> all, int page, int size) {
    int from = Math.min(page * size, all.size());
    return new PageResult<>(all.subList(from, Math.min(from + size, all.size())), page, size, all.size());
  }
}
