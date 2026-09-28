package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.activity.persistence.ActivityRepository;
import com.cloudticket.activity.persistence.entity.ActivityEntity;
import com.cloudticket.activity.persistence.mapper.ActivityMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ActivityRepositoryTest {

  @BeforeAll
  static void registerMetadata() {
    MybatisMetadata.register(ActivityEntity.class);
  }

  private final ActivityMapper mapper = mock(ActivityMapper.class);
  private final ActivityRepository repository = new ActivityRepository(mapper);

  @Test
  void freezeLayoutIsRejectedUntilTheActivityIsPublished() {
    when(mapper.selectById("activity-1")).thenReturn(entity("activity-1", "OFFLINE", false));

    assertThrows(IllegalStateException.class, () -> repository.freezeLayout("activity-1"));
    verify(mapper, never()).update(isNull(), any());
  }

  @Test
  void freezeLayoutIsRejectedTwice() {
    when(mapper.selectById("activity-1")).thenReturn(entity("activity-1", "PUBLISHED", true));

    assertThrows(IllegalStateException.class, () -> repository.freezeLayout("activity-1"));
    verify(mapper, never()).update(isNull(), any());
  }

  @Test
  void freezeLayoutMarksAPublishedActivityAsFrozen() {
    ActivityEntity published = entity("activity-1", "PUBLISHED", false);
    ActivityEntity frozen = entity("activity-1", "PUBLISHED", true);
    when(mapper.selectById("activity-1")).thenReturn(published, frozen);

    assertEquals(true, repository.freezeLayout("activity-1").layoutFrozen());
    verify(mapper).update(isNull(), any());
  }

  @Test
  void updateIsRejectedOnceTheLayoutIsFrozen() {
    when(mapper.selectById("activity-1")).thenReturn(entity("activity-1", "PUBLISHED", true));

    assertThrows(IllegalStateException.class, () -> repository.update("activity-1", "New", "Org"));
  }

  @Test
  void createRequiresATitle() {
    assertThrows(IllegalArgumentException.class, () -> repository.create("  ", "Org"));
  }

  @Test
  void createAndUpdatePreserveDescription() {
    ActivityEntity created = entity("activity-1", "OFFLINE", false);
    created.setDescription("  A detailed event description  ");
    when(mapper.selectById(any())).thenReturn(created);

    assertEquals("  A detailed event description  ", repository.create("A", "Org", "  A detailed event description  ").description());
    assertEquals("  A detailed event description  ", repository.update("activity-1", "A", "Org", "  A detailed event description  ").description());
  }

  @Test
  void unknownActivityIsNotFound() {
    assertThrows(java.util.NoSuchElementException.class, () -> repository.require("missing"));
  }

  private static ActivityEntity entity(String id, String status, boolean frozen) {
    ActivityEntity entity = new ActivityEntity();
    entity.setId(id);
    entity.setTitle("A");
    entity.setOrganizer("Org");
    entity.setStatus(status);
    entity.setLayoutFrozen(frozen);
    entity.setDescription("");
    return entity;
  }
}
