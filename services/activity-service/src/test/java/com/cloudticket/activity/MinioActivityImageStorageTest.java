package com.cloudticket.activity.image;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

class MinioActivityImageStorageTest {
  @Test
  void presignsWithThePublicEndpointClient() throws Exception {
    MinioClient internal = mock(MinioClient.class);
    MinioClient publicClient = mock(MinioClient.class);
    when(publicClient.getPresignedObjectUrl(org.mockito.ArgumentMatchers.any()))
        .thenReturn("http://localhost:9000/cloudticket-activity/object?signature=public");

    MinioActivityImageStorage storage = new MinioActivityImageStorage(internal, publicClient,
        "cloudticket-activity", 900);

    assertTrue(storage.presignedGet("object").contains("localhost:9000"));
  }
}
