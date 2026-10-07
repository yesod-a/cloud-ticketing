package com.cloudticket.activity.api;

import com.cloudticket.activity.api.command.ActivityCommands;
import com.cloudticket.activity.api.command.SessionCommands;
import com.cloudticket.activity.domain.Activity;
import com.cloudticket.activity.domain.Seat;
import com.cloudticket.activity.domain.Session;
import com.cloudticket.activity.service.ActivityService;
import com.cloudticket.activity.service.SessionService;
import com.cloudticket.activity.image.ActivityImageService;
import com.cloudticket.activity.persistence.entity.ActivityImageEntity;
import com.cloudticket.common.security.AuditAction;
import com.cloudticket.common.security.CallerContext;
import com.cloudticket.common.security.CallerContextHolder;
import com.cloudticket.common.security.RequirePermission;
import com.cloudticket.common.security.RequireScope;
import com.cloudticket.common.web.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

/**
 * Activity administration.
 *
 * <p>Every endpoint declares its permission, resource scope and audit action. The checks used to be
 * copy-pasted into each method, which is how an endpoint could ship without one.
 */
@RestController
@RequestMapping("/api/admin/activities")
public class ActivityAdminController {

  private final ActivityService activities;
  private final SessionService sessions;
  private final ActivityImageService images;

  public ActivityAdminController(ActivityService activities, SessionService sessions) {
    this(activities, sessions, null);
  }

  @Autowired
  public ActivityAdminController(ActivityService activities, SessionService sessions, ActivityImageService images) {
    this.activities = activities;
    this.sessions = sessions;
    this.images = images;
  }

  @RequirePermission({"activity:read", "activity:write", "activity:publish"})
  @GetMapping
  public Map<String, Object> list(@RequestParam(name = "keyword", defaultValue = "") String keyword,
                                  @RequestParam(name = "status", defaultValue = "") String status,
                                  @RequestParam(name = "page", defaultValue = "0") int page,
                                  @RequestParam(name = "size", defaultValue = "20") int size) {
    CallerContext caller = CallerContextHolder.current();
    return activities.adminActivities(keyword, status, page, size, caller.permissions(), caller.scopes()).asMap();
  }

  @RequirePermission({"activity:read", "activity:write", "activity:publish"})
  @GetMapping("/{id}/sessions")
  public Map<String, Object> sessions(@PathVariable("id") String id,
                                      @RequestParam(name = "status", defaultValue = "") String status,
                                      @RequestParam(name = "page", defaultValue = "0") int page,
                                      @RequestParam(name = "size", defaultValue = "20") int size) {
    CallerContext caller = CallerContextHolder.current();
    return sessions.adminSessions(id, status, page, size, caller.permissions(), caller.scopes()).asMap();
  }

  @RequirePermission({"activity:read", "activity:write", "activity:publish"})
  @GetMapping("/{id}/seats")
  public Map<String, Object> seats(@PathVariable("id") String id,
                                   @RequestParam(name = "page", defaultValue = "0") int page,
                                   @RequestParam(name = "size", defaultValue = "20") int size) {
    CallerContext caller = CallerContextHolder.current();
    return activities.seatsForActivity(id, caller.permissions(), caller.scopes(), page, size).asMap();
  }

  @RequirePermission("audit:read")
  @GetMapping("/{id}/audits")
  public Map<String, Object> audits(@PathVariable("id") String id,
                                    @RequestParam(name = "action", defaultValue = "") String action,
                                    @RequestParam(name = "page", defaultValue = "0") int page,
                                    @RequestParam(name = "size", defaultValue = "20") int size) {
    return activities.audits(action, page, size).asMap();
  }

  @RequirePermission("activity:write")
  @AuditAction(action = "ACTIVITY_CREATED", resourceType = "ACTIVITY", resourceId = "#result.id()")
  @PostMapping
  public Activity create(@RequestBody ActivityCommands.CreateActivity body) {
    return body.description() == null || body.description().isBlank()
        ? activities.create(body.title(), body.organizer())
        : activities.create(body.title(), body.organizer(), body.description());
  }

  @RequirePermission("activity:write")
  @RequireScope(type = "ACTIVITY", id = "#id")
  @AuditAction(action = "ACTIVITY_UPDATED", resourceType = "ACTIVITY",
      before = "@activitySnapshots.activity(#id)")
  @PutMapping("/{id}")
  public Activity update(@PathVariable("id") String id, @RequestBody ActivityCommands.UpdateActivity body) {
    return body.description() == null || body.description().isBlank()
        ? activities.update(id, body.title(), body.organizer())
        : activities.update(id, body.title(), body.organizer(), body.description());
  }

  @RequirePermission("session:write")
  @RequireScope(type = "ACTIVITY", id = "#id")
  @AuditAction(action = "SESSION_CREATED", resourceType = "SESSION", resourceId = "#result.id()")
  @PostMapping("/{id}/sessions")
  public Session createSession(@PathVariable("id") String id, @RequestBody SessionCommands.CreateSession body) {
    if (body.layoutMode() == null && body.capacity() == null && body.purchaseLimit() == null) {
      return sessions.create(id, body.venueId(), body.startsAt(), body.endsAt(), body.statusOrDefault(),
          body.priceOrZero());
    }
    return sessions.create(id, body.venueId(), body.startsAt(), body.endsAt(), body.statusOrDefault(),
        body.priceOrZero(), body.layoutModeOrDefault(), body.capacityOrZero(), body.purchaseLimitOrZero(),
        body.saleModeOrDefault(), body.saleStartAt());
  }

  @RequirePermission("session:write")
  @RequireScope(type = "ACTIVITY", id = "#activityId")
  @AuditAction(action = "SESSION_UPDATED", resourceType = "SESSION", resourceId = "#sessionId")
  @PutMapping("/{activityId}/sessions/{sessionId}")
  public Session updateSession(@PathVariable("activityId") String activityId,
                               @PathVariable("sessionId") String sessionId,
                               @RequestBody SessionCommands.UpdateSession body) {
    if (body.layoutMode() == null && body.capacity() == null && body.purchaseLimit() == null) {
      return sessions.update(sessionId, body.startsAt(), body.endsAt(), body.statusOrDefault(), body.priceOrZero(),
          "GRID", 0, 0, body.saleModeOrDefault(), body.saleStartAt());
    }
    return sessions.update(sessionId, body.startsAt(), body.endsAt(), body.statusOrDefault(), body.priceOrZero(),
        body.layoutModeOrDefault(), body.capacityOrZero(), body.purchaseLimitOrZero(), body.saleModeOrDefault(),
        body.saleStartAt());
  }

  @RequirePermission({"session:write", "activity:publish"})
  @RequireScope(type = "ACTIVITY", id = "#activityId")
  @AuditAction(action = "SESSION_PUBLISHED", resourceType = "SESSION", resourceId = "#sessionId")
  @PostMapping("/{activityId}/sessions/{sessionId}/publish")
  public Session publishSession(@PathVariable("activityId") String activityId,
                                @PathVariable("sessionId") String sessionId) {
    return sessions.publish(sessionId);
  }

  @RequirePermission({"session:write", "activity:publish"})
  @RequireScope(type = "ACTIVITY", id = "#activityId")
  @AuditAction(action = "SESSION_OFFLINED", resourceType = "SESSION", resourceId = "#sessionId")
  @PostMapping("/{activityId}/sessions/{sessionId}/offline")
  public Session offlineSession(@PathVariable("activityId") String activityId,
                                @PathVariable("sessionId") String sessionId) {
    return sessions.offline(sessionId);
  }

  @RequirePermission("session:write")
  @RequireScope(type = "ACTIVITY", id = "#activityId")
  @AuditAction(action = "SESSION_DELETED", resourceType = "SESSION", resourceId = "#sessionId",
      before = "", after = "")
  @DeleteMapping("/{activityId}/sessions/{sessionId}")
  public Map<String, Object> deleteSession(@PathVariable("activityId") String activityId,
                                           @PathVariable("sessionId") String sessionId) {
    sessions.delete(sessionId);
    return ApiResponse.code("OK");
  }

  @RequirePermission("activity:write")
  @RequireScope(type = "ACTIVITY", id = "#id")
  @AuditAction(action = "ACTIVITY_DELETED", resourceType = "ACTIVITY", resourceId = "#id",
      before = "", after = "")
  @DeleteMapping("/{id}")
  public Map<String, Object> deleteActivity(@PathVariable("id") String id) {
    activities.delete(id);
    return ApiResponse.code("OK");
  }

  @RequirePermission({"seat-layout:write", "inventory:adjust"})
  @RequireScope(type = "ACTIVITY", id = "@activitySnapshots.activityForSeat(#id)")
  @AuditAction(action = "SEAT_UPDATED", resourceType = "SEAT", resourceId = "#id",
      before = "@activitySnapshots.seat(#id)")
  @PutMapping("/seats/{id}")
  public Seat updateSeat(@PathVariable("id") String id, @RequestBody SessionCommands.UpdateSeat body) {
    return sessions.updateSeat(id, body.statusOrDefault());
  }

  @RequirePermission("activity:publish")
  @RequireScope(type = "ACTIVITY", id = "#id")
  @AuditAction(action = "ACTIVITY_PUBLISHED", resourceType = "ACTIVITY",
      before = "@activitySnapshots.activity(#id)")
  @PostMapping("/{id}/publish")
  public Activity publish(@PathVariable("id") String id) {
    return activities.publish(id);
  }

  @RequirePermission("activity:publish")
  @RequireScope(type = "ACTIVITY", id = "#id")
  @AuditAction(action = "ACTIVITY_OFFLINED", resourceType = "ACTIVITY",
      before = "@activitySnapshots.activity(#id)")
  @PostMapping("/{id}/offline")
  public Activity offline(@PathVariable("id") String id) {
    return activities.offline(id);
  }

  @RequirePermission("seat-layout:write")
  @RequireScope(type = "ACTIVITY", id = "#id")
  @AuditAction(action = "LAYOUT_FROZEN", resourceType = "ACTIVITY",
      before = "@activitySnapshots.activity(#id)")
  @PostMapping("/{id}/layout/freeze")
  public Activity freeze(@PathVariable("id") String id) {
    return activities.freezeLayout(id);
  }

  @RequirePermission("activity:write")
  @RequireScope(type = "ACTIVITY", id = "#id")
  @AuditAction(action = "ACTIVITY_IMAGE_UPLOADED", resourceType = "ACTIVITY", resourceId = "#id")
  @PostMapping(value = "/{id}/images", consumes = "multipart/form-data")
  public Map<String, Object> uploadImage(@PathVariable("id") String id,
      @RequestPart("file") MultipartFile file,
      @RequestParam(name = "imageType", defaultValue = "DETAIL") String imageType) {
    ActivityImageEntity image = images.upload(id, file, imageType);
    return ActivityImageViews.view(image, images);
  }

  @RequirePermission("activity:write")
  @RequireScope(type = "ACTIVITY", id = "#id")
  @AuditAction(action = "ACTIVITY_IMAGE_DELETED", resourceType = "ACTIVITY", resourceId = "#id",
      before = "", after = "")
  @DeleteMapping("/{id}/images/{imageId}")
  public Map<String, Object> deleteImage(@PathVariable("id") String id, @PathVariable("imageId") String imageId) {
    images.delete(id, imageId);
    return ApiResponse.code("OK");
  }

  @RequirePermission("activity:write")
  @RequireScope(type = "ACTIVITY", id = "#id")
  @AuditAction(action = "ACTIVITY_IMAGE_COVER_SET", resourceType = "ACTIVITY", resourceId = "#id")
  @PutMapping("/{id}/images/{imageId}/cover")
  public Map<String, Object> setCover(@PathVariable("id") String id,
      @PathVariable("imageId") String imageId) {
    ActivityImageEntity image = images.setCover(id, imageId);
    return ActivityImageViews.view(image, images);
  }

  @RequirePermission("activity:write")
  @RequireScope(type = "ACTIVITY", id = "#id")
  @AuditAction(action = "ACTIVITY_IMAGE_REORDERED", resourceType = "ACTIVITY", resourceId = "#id",
      before = "", after = "#body['imageIds']")
  @PutMapping("/{id}/images/order")
  public Map<String, Object> reorderImages(@PathVariable("id") String id,
      @RequestBody Map<String, List<String>> body) {
    images.reorder(id, body == null ? List.of() : body.getOrDefault("imageIds", List.of()));
    return ApiResponse.code("OK");
  }
}
