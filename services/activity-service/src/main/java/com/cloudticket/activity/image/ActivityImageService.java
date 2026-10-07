package com.cloudticket.activity.image;

import com.cloudticket.activity.api.ActivityImageViews;
import com.cloudticket.activity.persistence.ActivityImageRepository;
import com.cloudticket.activity.persistence.entity.ActivityImageEntity;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ActivityImageService {
  private static final Logger log = LoggerFactory.getLogger(ActivityImageService.class);
  static final long MAX_BYTES = 5L * 1024 * 1024;
  private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
  private final ActivityImageRepository images;
  private final ActivityImageStorage storage;
  public ActivityImageService(ActivityImageRepository images, ActivityImageStorage storage) { this.images=images; this.storage=storage; }

  @Transactional public ActivityImageEntity upload(String activityId, MultipartFile file, String imageType) {
    if (activityId==null || activityId.isBlank()) throw new IllegalArgumentException("activity required");
    images.lockActivity(activityId);
    String type=normalizeType(imageType), content=normalizeContentType(file); long size=file.getSize();
    if (size<=0 || size>MAX_BYTES) throw new IllegalArgumentException("Image must be between 1 byte and 5 MiB");
    if (!CONTENT_TYPES.contains(content)) throw new IllegalArgumentException("Image must be JPEG, PNG or WebP");
    if ("DETAIL".equals(type) && images.activeDetails(activityId).size()>=9) throw new IllegalStateException("activity detail image limit reached");
    ActivityImageEntity previous="COVER".equals(type)?images.activeCover(activityId).orElse(null):null;
    String key="activities/"+activityId+"/"+UUID.randomUUID()+extension(content);
    try {
      byte[] bytes=file.getBytes(); validateSignature(bytes, content); storage.put(key,content,new ByteArrayInputStream(bytes),size);
      ActivityImageEntity entity=new ActivityImageEntity(); entity.setId(UUID.randomUUID().toString()); entity.setActivityId(activityId); entity.setObjectKey(key); entity.setOriginalName(file.getOriginalFilename()==null?"image":file.getOriginalFilename()); entity.setContentType(content); entity.setSizeBytes(size); entity.setImageType(type); entity.setSortOrder("COVER".equals(type)?0:images.activeDetails(activityId).size()); entity.setStatus("ACTIVE");
      ActivityImageEntity saved=images.save(entity);
      if(previous!=null) removeObject(previous);
      return saved;
    } catch(Exception failure){try{storage.delete(key);}catch(Exception ignored){} if(failure instanceof IllegalArgumentException a) throw a; throw new IllegalStateException("Could not persist activity image",failure);}
  }

  @Transactional public void delete(String activityId,String imageId){images.lockActivity(activityId); ActivityImageEntity image=images.activeById(activityId,imageId).orElseThrow(()->new IllegalArgumentException("active image not found")); removeObject(image);}
  @Transactional public ActivityImageEntity setCover(String activityId,String imageId){images.lockActivity(activityId); ActivityImageEntity selected=images.activeById(activityId,imageId).orElseThrow(()->new IllegalArgumentException("active image not found")); if("COVER".equals(selected.getImageType()))return selected; ActivityImageEntity old=images.activeCover(activityId).orElse(null); if(old!=null) removeObject(old); images.updateType(selected.getId(),"COVER",0); selected.setImageType("COVER");selected.setSortOrder(0);return selected;}
  @Transactional public void reorder(String activityId,List<String> ids){images.lockActivity(activityId); List<ActivityImageEntity> active=images.activeDetails(activityId);List<String> requested=ids==null?List.of():List.copyOf(ids);Set<String> expected=active.stream().map(ActivityImageEntity::getId).collect(java.util.stream.Collectors.toSet());if(requested.size()!=expected.size()||new HashSet<>(requested).size()!=requested.size()||!expected.equals(new HashSet<>(requested)))throw new IllegalArgumentException("order must contain exactly the active detail image ids");for(int i=0;i<requested.size();i++)images.updateSortOrder(requested.get(i),i);}
  public ActivityImageViews.ActivityImages publicImages(String id){String cover=images.activeCover(id).map(x->presigned(x.getObjectKey())).orElse(null);List<ActivityImageViews.Image> details=new ArrayList<>();for(ActivityImageEntity image:images.activeDetails(id))details.add(ActivityImageViews.image(image,presigned(image.getObjectKey())));return new ActivityImageViews.ActivityImages(cover,details);}
  private String presigned(String key){try{return storage.presignedGet(key);}catch(Exception e){throw new IllegalStateException("Could not create activity image URL",e);}}
  public List<ActivityImageEntity> active(String id){return images.active(id);}
  public java.util.Optional<ActivityImageEntity> cover(String id){return images.activeCover(id);}
  public String url(ActivityImageEntity image){return presigned(image.getObjectKey());}
  @Scheduled(fixedDelayString = "${cloudticket.activity-image-cleanup-ms:60000}")
  public void cleanupPending() {
    for (ActivityImageEntity image : images.pendingCleanup()) {
      try { storage.delete(image.getObjectKey()); images.markDeleted(image.getId()); }
      catch (Exception failure) { log.warn("Activity image cleanup failed for {}", image.getId(), failure); }
    }
  }
  private void removeObject(ActivityImageEntity image) {
    try { storage.delete(image.getObjectKey()); images.markDeleted(image.getId()); }
    catch (Exception failure) { images.markDeletePending(image.getId()); log.warn("Activity image deletion deferred for {}", image.getId(), failure); }
  }
  private static String normalizeType(String value){String type=value==null?"":value.trim().toUpperCase(Locale.ROOT);if(!"COVER".equals(type)&&!"DETAIL".equals(type))throw new IllegalArgumentException("imageType must be COVER or DETAIL");return type;}
  private static String normalizeContentType(MultipartFile file){if(file==null||file.isEmpty())throw new IllegalArgumentException("Image is required");return file.getContentType()==null?"":file.getContentType().toLowerCase(Locale.ROOT);}
  private static String extension(String type){return switch(type){case "image/jpeg"->".jpg";case "image/png"->".png";default->".webp";};}
  private static void validateSignature(byte[] bytes, String content) {
    boolean valid = switch (content) {
      case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
      case "image/png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47;
      case "image/webp" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
          && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
      default -> false;
    };
    if (!valid) throw new IllegalArgumentException("Image content does not match its MIME type");
  }
}
