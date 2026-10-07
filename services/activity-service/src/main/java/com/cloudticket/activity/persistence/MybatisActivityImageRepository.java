package com.cloudticket.activity.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.cloudticket.activity.persistence.entity.ActivityImageEntity;
import com.cloudticket.activity.persistence.mapper.ActivityImageMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisActivityImageRepository implements ActivityImageRepository {
  private final ActivityImageMapper images;
  public MybatisActivityImageRepository(ActivityImageMapper images) { this.images = images; }
  @Override public void lockActivity(String activityId) { images.lockActivity(activityId); }
  @Override public Optional<ActivityImageEntity> activeCover(String id) { return Optional.ofNullable(images.selectOne(Wrappers.<ActivityImageEntity>lambdaQuery().eq(ActivityImageEntity::getActivityId,id).eq(ActivityImageEntity::getImageType,"COVER").eq(ActivityImageEntity::getStatus,"ACTIVE").last("LIMIT 1"))); }
  @Override public Optional<ActivityImageEntity> activeById(String activityId, String imageId) { return Optional.ofNullable(images.selectOne(Wrappers.<ActivityImageEntity>lambdaQuery().eq(ActivityImageEntity::getActivityId,activityId).eq(ActivityImageEntity::getId,imageId).eq(ActivityImageEntity::getStatus,"ACTIVE").last("LIMIT 1"))); }
  @Override public List<ActivityImageEntity> activeDetails(String id) { return images.selectList(Wrappers.<ActivityImageEntity>lambdaQuery().eq(ActivityImageEntity::getActivityId,id).eq(ActivityImageEntity::getImageType,"DETAIL").eq(ActivityImageEntity::getStatus,"ACTIVE").orderByAsc(ActivityImageEntity::getSortOrder).orderByAsc(ActivityImageEntity::getId)); }
  @Override public List<ActivityImageEntity> active(String id) { return images.selectList(Wrappers.<ActivityImageEntity>lambdaQuery().eq(ActivityImageEntity::getActivityId,id).eq(ActivityImageEntity::getStatus,"ACTIVE").orderByAsc(ActivityImageEntity::getImageType).orderByAsc(ActivityImageEntity::getSortOrder)); }
  @Override public ActivityImageEntity save(ActivityImageEntity image) { images.insert(image); return image; }
  @Override public Optional<ActivityImageEntity> find(String id) { return Optional.ofNullable(images.selectById(id)); }
  @Override public void deactivate(String id) { images.update(null, Wrappers.<ActivityImageEntity>lambdaUpdate().set(ActivityImageEntity::getStatus,"DELETED").eq(ActivityImageEntity::getId,id)); }
  @Override public void markDeletePending(String id) { images.update(null, Wrappers.<ActivityImageEntity>lambdaUpdate().set(ActivityImageEntity::getStatus,"DELETE_PENDING").eq(ActivityImageEntity::getId,id)); }
  @Override public void markDeleted(String id) { images.update(null, Wrappers.<ActivityImageEntity>lambdaUpdate().set(ActivityImageEntity::getStatus,"DELETED").eq(ActivityImageEntity::getId,id)); }
  @Override public void markDeletePendingForActivity(String activityId) { images.update(null, Wrappers.<ActivityImageEntity>lambdaUpdate().set(ActivityImageEntity::getStatus,"DELETE_PENDING").eq(ActivityImageEntity::getActivityId,activityId).in(ActivityImageEntity::getStatus,"ACTIVE","DELETE_PENDING")); }
  @Override public List<ActivityImageEntity> pendingCleanup() { return images.selectList(Wrappers.<ActivityImageEntity>lambdaQuery().eq(ActivityImageEntity::getStatus,"DELETE_PENDING").last("LIMIT 100")); }
  @Override public void updateSortOrder(String id, int order) { images.update(null, Wrappers.<ActivityImageEntity>lambdaUpdate().set(ActivityImageEntity::getSortOrder,order).eq(ActivityImageEntity::getId,id)); }
  @Override public void reorder(List<String> ids) { for (int i=0;i<ids.size();i++) updateSortOrder(ids.get(i),i); }
  @Override public void updateType(String id, String type, int order) { images.update(null, Wrappers.<ActivityImageEntity>lambdaUpdate().set(ActivityImageEntity::getImageType,type).set(ActivityImageEntity::getSortOrder,order).eq(ActivityImageEntity::getId,id).eq(ActivityImageEntity::getStatus,"ACTIVE")); }
}
