package com.cloudticket.activity.api;

import com.cloudticket.activity.service.ActivityService;
import com.cloudticket.activity.service.SessionService;
import com.cloudticket.activity.image.ActivityImageService;
import com.cloudticket.activity.persistence.entity.ActivityImageEntity;
import com.cloudticket.common.web.PageResult;
import java.util.List;
import com.cloudticket.common.web.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Anonymous catalogue: only activities that currently have an on-sale session are listed. */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {

  private final ActivityService activities;
  private final SessionService sessions;
  private final ActivityImageService images;

  public ActivityController(ActivityService activities, SessionService sessions) {
    this(activities, sessions, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public ActivityController(ActivityService activities, SessionService sessions, ActivityImageService images) {
    this.activities = activities;
    this.sessions = sessions;
    this.images = images;
  }

  @GetMapping
  public Map<String, Object> list(@RequestParam(name = "keyword", defaultValue = "") String keyword,
                                  @RequestParam(name = "organizer", defaultValue = "") String organizer,
                                  @RequestParam(name = "page", defaultValue = "0") int page,
                                  @RequestParam(name = "size", defaultValue = "12") int size) {
    PageResult<com.cloudticket.activity.domain.Activity> result = activities.publicActivities(keyword, organizer, page, size);
    List<ActivityImageViews.PublicActivity> items = result.items().stream().map(activity -> {
      String cover = images == null ? null : images.publicImages(activity.id()).coverImageUrl();
      return ActivityImageViews.activity(activity, cover);
    }).toList();
    return ApiResponse.ok("ok", new PageResult<>(items, result.page(), result.size(), result.total()).asMap());
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable("id") String id) {
    var activity = activities.require(id);
    var onSaleSessions = sessions.listOnSale(id);
    if (!"PUBLISHED".equalsIgnoreCase(activity.status()) || onSaleSessions.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "activity not found");
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("activity", activity);
    payload.put("sessions", onSaleSessions);
    if (images != null) {
      var imageView = images.publicImages(id);
      payload.put("coverImageUrl", imageView.coverImageUrl());
      payload.put("images", imageView.images());
    }
    return ApiResponse.ok("ok", payload);
  }
}
