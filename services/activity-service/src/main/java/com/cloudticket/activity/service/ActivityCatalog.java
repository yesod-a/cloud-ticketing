package com.cloudticket.activity.service;
import java.util.*; import java.util.concurrent.ConcurrentHashMap; import org.springframework.stereotype.Service;
@Service public class ActivityCatalog {
 private final Map<String,Activity> activities=new ConcurrentHashMap<>();
 public ActivityCatalog(){ seed("星河现场 · 城市之声","星河文化","PUBLISHED"); seed("海岸线音乐节","海岸现场","PUBLISHED"); }
 private void seed(String title,String organizer,String status){Activity a=create(title,organizer); activities.put(a.id(),new Activity(a.id(),a.title(),a.organizer(),status,false));}
 public Activity create(String title,String organizer){if(title==null||title.isBlank())throw new IllegalArgumentException("title required"); Activity a=new Activity(UUID.randomUUID().toString(),title.trim(),organizer==null?"":organizer.trim(),"DRAFT",false);activities.put(a.id(),a);return a;}
 public List<Activity> publicActivities(){return activities.values().stream().filter(a->"PUBLISHED".equals(a.status())).sorted(Comparator.comparing(Activity::title)).toList();}
 public Activity get(String id){Activity a=activities.get(id);if(a==null)throw new NoSuchElementException("activity not found");return a;}
 public Activity publish(String id){Activity a=get(id); Activity p=new Activity(a.id(),a.title(),a.organizer(),"PUBLISHED",a.layoutFrozen());activities.put(id,p);return p;}
 public Activity offline(String id){Activity a=get(id);Activity p=new Activity(a.id(),a.title(),a.organizer(),"OFFLINE",a.layoutFrozen());activities.put(id,p);return p;}
 public Activity freezeLayout(String id){Activity a=get(id);if(!"PUBLISHED".equals(a.status())||a.layoutFrozen())throw new IllegalStateException("published layout can only be frozen once");Activity p=new Activity(a.id(),a.title(),a.organizer(),a.status(),true);activities.put(id,p);return p;}
 public record Activity(String id,String title,String organizer,String status,boolean layoutFrozen){}
}
