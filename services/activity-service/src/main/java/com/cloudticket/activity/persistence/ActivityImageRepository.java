package com.cloudticket.activity.persistence;

import com.cloudticket.activity.persistence.entity.ActivityImageEntity;
import java.util.List;
import java.util.Optional;

/** Metadata persistence boundary for activity images. */
public interface ActivityImageRepository {
  Optional<ActivityImageEntity> activeCover(String activityId);
  /** Locks the owning activity row for a mutation transaction. */
  void lockActivity(String activityId);
  Optional<ActivityImageEntity> activeById(String activityId, String imageId);
  List<ActivityImageEntity> activeDetails(String activityId);
  ActivityImageEntity save(ActivityImageEntity image);
  void deactivate(String id);
  void markDeletePending(String id);
  void markDeleted(String id);
  void markDeletePendingForActivity(String activityId);
  List<ActivityImageEntity> pendingCleanup();
  Optional<ActivityImageEntity> find(String id);
  void updateSortOrder(String id, int sortOrder);
  List<ActivityImageEntity> active(String activityId);
  void reorder(List<String> ids);
  void updateType(String id, String imageType, int sortOrder);
}
