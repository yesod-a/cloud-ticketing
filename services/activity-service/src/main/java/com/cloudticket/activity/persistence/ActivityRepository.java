package com.cloudticket.activity.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudticket.activity.domain.Activity;
import com.cloudticket.activity.persistence.entity.ActivityEntity;
import com.cloudticket.activity.persistence.mapper.ActivityMapper;
import com.cloudticket.common.web.PageResult;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Persistence and lifecycle transitions for {@code activity}. */
@Repository
public class ActivityRepository {

  public static final String PUBLISHED = "PUBLISHED";
  public static final String OFFLINE = "OFFLINE";

  private final ActivityMapper activities;

  public ActivityRepository(ActivityMapper activities) {
    this.activities = activities;
  }

  public Activity create(String title, String organizer) {
    String cleanTitle = requireTitle(title);
    ActivityEntity entity = new ActivityEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setTitle(cleanTitle);
    entity.setOrganizer(Text.orEmpty(organizer));
    entity.setStatus(OFFLINE);
    entity.setLayoutFrozen(false);
    activities.insert(entity);
    return toDomain(activities.selectById(entity.getId()));
  }

  public Activity update(String id, String title, String organizer) {
    ActivityEntity current = requireEntity(id);
    if (Boolean.TRUE.equals(current.getLayoutFrozen())) throw new IllegalStateException("layout is frozen");
    String cleanTitle = requireTitle(title);
    activities.update(null, Wrappers.<ActivityEntity>lambdaUpdate()
        .set(ActivityEntity::getTitle, cleanTitle)
        .set(ActivityEntity::getOrganizer, Text.orEmpty(organizer))
        .eq(ActivityEntity::getId, id));
    return toDomain(activities.selectById(id));
  }

  public Activity publish(String id) {
    require(id);
    return transition(id, PUBLISHED);
  }

  public Activity offline(String id) {
    require(id);
    return transition(id, OFFLINE);
  }

  /** A published layout is frozen exactly once so historical sessions keep a stable seat map. */
  public Activity freezeLayout(String id) {
    ActivityEntity current = requireEntity(id);
    if (!PUBLISHED.equals(current.getStatus()) || Boolean.TRUE.equals(current.getLayoutFrozen())) {
      throw new IllegalStateException("published layout can only be frozen once");
    }
    activities.update(null, Wrappers.<ActivityEntity>lambdaUpdate()
        .set(ActivityEntity::getLayoutFrozen, true)
        .eq(ActivityEntity::getId, id));
    return toDomain(activities.selectById(id));
  }

  /** Recomputes the activity status from its sessions instead of trusting a second write. */
  public void refreshStatus(String activityId) {
    activities.refreshStatus(activityId);
  }

  public void delete(String id) {
    activities.deleteById(id);
  }

  public Optional<Activity> find(String id) {
    return Optional.ofNullable(activities.selectById(id)).map(ActivityRepository::toDomain);
  }

  public Activity require(String id) {
    return find(id).orElseThrow(() -> new NoSuchElementException("activity not found"));
  }

  private ActivityEntity requireEntity(String id) {
    ActivityEntity entity = activities.selectById(id);
    if (entity == null) throw new NoSuchElementException("activity not found");
    return entity;
  }

  public List<Activity> publicActivities() {
    return activities.selectPublic().stream().map(ActivityRepository::toDomain).toList();
  }

  public PageResult<Activity> publicActivities(String keyword, String organizer, int page, int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<ActivityEntity> result = activities.selectPublicPage(new Page<>(safePage + 1L, safeSize),
        Text.orEmpty(keyword).trim(), Text.orEmpty(organizer).trim());
    return toPage(result, safePage, safeSize);
  }

  public PageResult<Activity> adminActivities(String keyword, String status, int page, int size) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<ActivityEntity> result = activities.selectPage(new Page<>(safePage + 1L, safeSize), adminFilter(keyword, status));
    return toPage(result, safePage, safeSize);
  }

  /** Listing for an operator limited to resource scopes; visibility is resolved in SQL. */
  public PageResult<Activity> adminActivitiesVisibleTo(String keyword, String status, int page, int size,
                                                       String scopes) {
    int safePage = PageResult.safePage(page);
    int safeSize = PageResult.safeSize(size);
    Page<ActivityEntity> result = activities.selectVisibleToPage(new Page<>(safePage + 1L, safeSize),
        Text.orEmpty(keyword).trim(), Text.orEmpty(status).trim(),
        Text.orEmpty(scopes).replace(" ", ""));
    return toPage(result, safePage, safeSize);
  }

  private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ActivityEntity> adminFilter(
      String keyword, String status) {
    String cleanKeyword = Text.orEmpty(keyword).trim();
    String cleanStatus = Text.orEmpty(status).trim();
    return Wrappers.<ActivityEntity>lambdaQuery()
        .like(!cleanKeyword.isBlank(), ActivityEntity::getTitle, cleanKeyword)
        .eq(!cleanStatus.isBlank(), ActivityEntity::getStatus, cleanStatus)
        .orderByAsc(ActivityEntity::getTitle);
  }

  private Activity transition(String id, String status) {
    activities.update(null, Wrappers.<ActivityEntity>lambdaUpdate()
        .set(ActivityEntity::getStatus, status)
        .eq(ActivityEntity::getId, id));
    return toDomain(activities.selectById(id));
  }

  private static PageResult<Activity> toPage(Page<ActivityEntity> result, int page, int size) {
    return new PageResult<>(result.getRecords().stream().map(ActivityRepository::toDomain).toList(),
        page, size, result.getTotal());
  }

  private static Activity toDomain(ActivityEntity entity) {
    return new Activity(entity.getId(), entity.getTitle(), entity.getOrganizer(), entity.getStatus(),
        Boolean.TRUE.equals(entity.getLayoutFrozen()));
  }

  private static String requireTitle(String title) {
    if (title == null || title.isBlank()) throw new IllegalArgumentException("title required");
    return title.trim();
  }
}
