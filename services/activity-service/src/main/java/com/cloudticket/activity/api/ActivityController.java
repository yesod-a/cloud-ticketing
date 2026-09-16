package com.cloudticket.activity.api;
import com.cloudticket.activity.service.ActivityCatalog; import java.util.*; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/activities") public class ActivityController {
  private final ActivityCatalog catalog;
  public ActivityController(ActivityCatalog catalog){this.catalog=catalog;}
  @GetMapping public Map<String,Object> list(@RequestParam(name="keyword",defaultValue="") String keyword,@RequestParam(name="organizer",defaultValue="") String organizer,@RequestParam(name="page",defaultValue="0") int page,@RequestParam(name="size",defaultValue="12") int size){var result=catalog.publicActivities(keyword,organizer,page,size);return Map.of("code","OK","message","ok","traceId","","data",Map.of("items",result.items(),"page",result.page(),"size",result.size(),"total",result.total(),"totalPages",result.totalPages()));}
  @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable("id") String id){var a=catalog.get(id);return Map.of("code","OK","message","ok","traceId","","data",Map.of("activity",a,"sessions",catalog.sessions(id)));}
}
