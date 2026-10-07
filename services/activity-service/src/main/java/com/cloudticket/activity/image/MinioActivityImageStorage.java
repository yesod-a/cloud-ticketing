package com.cloudticket.activity.image;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MinioActivityImageStorage implements ActivityImageStorage {
  private final MinioClient client;
  private final MinioClient presigningClient;
  private final String bucket;
  private final int urlExpirySeconds;

  @Autowired
  public MinioActivityImageStorage(
      @Value("${cloudticket.minio.endpoint:http://localhost:9000}") String endpoint,
      @Value("${cloudticket.minio.access-key:minioadmin}") String accessKey,
      @Value("${cloudticket.minio.secret-key:minioadmin}") String secretKey,
      @Value("${cloudticket.minio.bucket:cloudticket-activity}") String bucket,
      @Value("${cloudticket.minio.url-expiry-seconds:900}") int urlExpirySeconds,
      @Value("${cloudticket.minio.public-endpoint:}") String publicEndpoint) {
    this.client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey)
        .region("us-east-1").build();
    String signingEndpoint = publicEndpoint == null || publicEndpoint.isBlank() ? endpoint : publicEndpoint;
    this.presigningClient = signingEndpoint.equals(endpoint)
        ? this.client
        : MinioClient.builder().endpoint(signingEndpoint).credentials(accessKey, secretKey)
            .region("us-east-1").build();
    this.bucket = bucket;
    this.urlExpirySeconds = urlExpirySeconds;
  }

  MinioActivityImageStorage(MinioClient client, MinioClient presigningClient, String bucket,
                            int urlExpirySeconds) {
    this.client = client;
    this.presigningClient = presigningClient;
    this.bucket = bucket;
    this.urlExpirySeconds = urlExpirySeconds;
  }

  @Override public void put(String key, String contentType, InputStream body, long size) throws Exception {
    client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(body, size, -1)
        .contentType(contentType).build());
  }

  @Override public void delete(String key) throws Exception {
    client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
  }

  @Override public String presignedGet(String key) throws Exception {
    return presigningClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
        .bucket(bucket).object(key).expiry(urlExpirySeconds, TimeUnit.SECONDS).build());
  }
}
