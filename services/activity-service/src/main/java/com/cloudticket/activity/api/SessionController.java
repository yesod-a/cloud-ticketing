package com.cloudticket.activity.api;
import com.cloudticket.activity.service.ActivityCatalog; import java.util.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/sessions") public class SessionController { private final ActivityCatalog catalog; public SessionController(ActivityCatalog catalog){this.catalog=catalog;} @GetMapping("/{id}/seats") public Map<String,Object> seats(@PathVariable("id") String id){return Map.of("code","OK","message","ok","traceId","","data",catalog.seats(id));} }
