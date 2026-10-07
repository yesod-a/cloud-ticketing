package com.cloudticket.activity.api;

import com.cloudticket.activity.persistence.entity.ActivityImageEntity;
import com.cloudticket.activity.domain.Activity;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import com.cloudticket.activity.image.ActivityImageService;

/** Public image projection; object keys and storage credentials never cross the API boundary. */
public final class ActivityImageViews {

  private ActivityImageViews() {}

  public record Image(String id, String url, int sortOrder) {}

  public record ActivityImages(String coverImageUrl, List<Image> images) {
    public ActivityImages {
      images = images == null ? List.of() : List.copyOf(images);
    }
  }

  public record PublicActivity(String id, String title, String organizer, String description,
                               String status, boolean layoutFrozen, String coverImageUrl) {}

  public static PublicActivity activity(Activity activity, String coverImageUrl) {
    return new PublicActivity(activity.id(), activity.title(), activity.organizer(), activity.description(),
        activity.status(), activity.layoutFrozen(), coverImageUrl);
  }

  public static Image image(ActivityImageEntity image, String url) {
    return new Image(image.getId(), url, image.getSortOrder() == null ? 0 : image.getSortOrder());
  }

  public static Map<String, Object> view(ActivityImageEntity image, ActivityImageService service) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", image.getId());
    value.put("imageType", image.getImageType());
    value.put("sortOrder", image.getSortOrder());
    value.put("url", service.url(image));
    return value;
  }

  public static List<Map<String, Object>> list(List<ActivityImageEntity> images, ActivityImageService service) {
    return images.stream().map(image -> view(image, service)).toList();
  }
}
