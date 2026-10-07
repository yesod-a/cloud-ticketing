package com.cloudticket.activity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.activity.image.ActivityImageService;
import com.cloudticket.activity.image.ActivityImageStorage;
import com.cloudticket.activity.persistence.ActivityImageRepository;
import com.cloudticket.activity.persistence.entity.ActivityImageEntity;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ActivityImageServiceTest {

  private final ActivityImageRepository images = mock(ActivityImageRepository.class);
  private final ActivityImageStorage storage = mock(ActivityImageStorage.class);
  private final ActivityImageService service = new ActivityImageService(images, storage);

  @Test
  void rejectsUnsupportedContentTypeBeforeStorageWrite() {
    MockMultipartFile file = new MockMultipartFile("file", "x.gif", "image/gif", new byte[] {1});

    assertThrows(IllegalArgumentException.class, () -> service.upload("a1", file, "DETAIL"));
  }

  @Test
  void replacesExistingCoverAndWritesGeneratedObjectKey() throws Exception {
    ActivityImageEntity old = entity("old", "old-key", "COVER", 0);
    when(images.activeCover("a1")).thenReturn(java.util.Optional.of(old));
    when(images.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    byte[] png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    MockMultipartFile file = new MockMultipartFile("file", "poster.png", "image/png", png);

    ActivityImageEntity result = service.upload("a1", file, "COVER");

    assertTrue(result.getObjectKey().startsWith("activities/a1/"));
    verify(storage).delete("old-key");
    verify(storage).put(eq(result.getObjectKey()), eq("image/png"), any(ByteArrayInputStream.class), eq((long) png.length));
  }

  @Test
  void rejectsTheTenthDetailImage() {
    when(images.activeDetails("a1")).thenReturn(List.of(
        entity("1", "1", "DETAIL", 0), entity("2", "2", "DETAIL", 1), entity("3", "3", "DETAIL", 2),
        entity("4", "4", "DETAIL", 3), entity("5", "5", "DETAIL", 4), entity("6", "6", "DETAIL", 5),
        entity("7", "7", "DETAIL", 6), entity("8", "8", "DETAIL", 7), entity("9", "9", "DETAIL", 8)));

    MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", new byte[] {1});
    assertThrows(IllegalStateException.class, () -> service.upload("a1", file, "DETAIL"));
  }

  @Test
  void deletesOnlyAnActiveImageOwnedByTheActivityAndRemovesTheObject() throws Exception {
    ActivityImageEntity image = entity("image-1", "activities/a1/x.jpg", "DETAIL", 0);
    when(images.activeById("a1", "image-1")).thenReturn(Optional.of(image));

    service.delete("a1", "image-1");

    verify(images).markDeleted("image-1");
    verify(storage).delete(image.getObjectKey());
  }

  @Test
  void keepsMetadataPendingWhenObjectDeletionFails() throws Exception {
    ActivityImageEntity image = entity("image-1", "activities/a1/x.jpg", "DETAIL", 0);
    when(images.activeById("a1", "image-1")).thenReturn(Optional.of(image));
    org.mockito.Mockito.doThrow(new IllegalStateException("minio down")).when(storage).delete(image.getObjectKey());

    service.delete("a1", "image-1");

    verify(images).markDeletePending("image-1");
  }

  @Test
  void reorderRequiresExactlyTheActiveDetailIds() {
    when(images.activeDetails("a1")).thenReturn(List.of(
        entity("one", "one", "DETAIL", 0), entity("two", "two", "DETAIL", 1)));

    service.reorder("a1", List.of("two", "one"));

    verify(images).updateSortOrder("two", 0);
    verify(images).updateSortOrder("one", 1);
  }

  @Test
  void createsPublicImageViewsWithPresignedUrls() throws Exception {
    ActivityImageEntity cover = entity("cover", "cover-key", "COVER", 0);
    ActivityImageEntity detail = entity("detail", "detail-key", "DETAIL", 0);
    when(images.activeCover("a1")).thenReturn(Optional.of(cover));
    when(images.activeDetails("a1")).thenReturn(List.of(detail));
    when(storage.presignedGet("cover-key")).thenReturn("https://minio/cover");
    when(storage.presignedGet("detail-key")).thenReturn("https://minio/detail");

    var view = service.publicImages("a1");

    assertEquals("https://minio/cover", view.coverImageUrl());
    assertEquals("https://minio/detail", view.images().get(0).url());
  }

  private static ActivityImageEntity entity(String id, String key, String type, int order) {
    ActivityImageEntity entity = new ActivityImageEntity();
    entity.setId(id); entity.setActivityId("a1"); entity.setObjectKey(key);
    entity.setImageType(type); entity.setSortOrder(order); entity.setStatus("ACTIVE");
    return entity;
  }
}
