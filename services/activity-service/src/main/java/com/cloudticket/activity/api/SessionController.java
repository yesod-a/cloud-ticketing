package com.cloudticket.activity.api;

import com.cloudticket.activity.service.SessionService;
import com.cloudticket.common.web.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

  private final SessionService sessions;

  public SessionController(SessionService sessions) {
    this.sessions = sessions;
  }

  @GetMapping("/{id}/seats")
  public Map<String, Object> seats(@PathVariable("id") String id) {
    return ApiResponse.ok("ok", sessions.seatLayouts(id));
  }
}
