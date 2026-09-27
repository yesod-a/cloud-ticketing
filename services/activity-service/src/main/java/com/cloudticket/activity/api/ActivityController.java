package com.cloudticket.activity.api;

import com.cloudticket.activity.service.ActivityService;
import com.cloudticket.activity.service.SessionService;
import com.cloudticket.common.web.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Anonymous catalogue: only activities that currently have an on-sale session are listed. */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {

  private final ActivityService activities;
  private final SessionService sessions;

  public ActivityController(ActivityService activities, SessionService sessions) {
    this.activities = activities;
    this.sessions = sessions;
  }

  @GetMapping
  public Map<String, Object> list(@RequestParam(name = "keyword", defaultValue = "") String keyword,
                                  @RequestParam(name = "organizer", defaultValue = "") String organizer,
                                  @RequestParam(name = "page", defaultValue = "0") int page,
                                  @RequestParam(name = "size", defaultValue = "12") int size) {
    return ApiResponse.ok("ok", activities.publicActivities(keyword, organizer, page, size).asMap());
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable("id") String id) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("activity", activities.require(id));
    payload.put("sessions", sessions.listOnSale(id));
    return ApiResponse.ok("ok", payload);
  }
}
