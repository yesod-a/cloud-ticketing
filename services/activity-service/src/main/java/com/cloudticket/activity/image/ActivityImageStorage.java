package com.cloudticket.activity.image;

import java.io.InputStream;

/** Binary storage boundary for activity images. */
public interface ActivityImageStorage {

  void put(String objectKey, String contentType, InputStream body, long size) throws Exception;

  void delete(String objectKey) throws Exception;

  String presignedGet(String objectKey) throws Exception;
}
