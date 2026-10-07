package com.cloudticket.auth.profile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/** Storage boundary for user avatar binaries. */
public interface AvatarStorage {

  long MAX_BYTES = 2L * 1024 * 1024;
  Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

  String save(java.util.UUID userId, MultipartFile file);

  Optional<StoredAvatar> open(String filename);

  void delete(String filename);

  static String extension(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
      throw new IllegalArgumentException("Avatar must be a non-empty image up to 2 MiB");
    }
    String contentType = file.getContentType() == null
        ? "" : file.getContentType().toLowerCase(Locale.ROOT);
    if (!CONTENT_TYPES.contains(contentType)) {
      throw new IllegalArgumentException("Avatar must be JPEG, PNG or WebP");
    }
    try {
      byte[] bytes = file.getBytes();
      boolean valid = switch (contentType) {
        case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
            && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        case "image/png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
            && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
            && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
        case "image/webp" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
            && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
            && bytes[10] == 'B' && bytes[11] == 'P';
        default -> false;
      };
      if (!valid) throw new IllegalArgumentException("Avatar content does not match its MIME type");
    } catch (java.io.IOException failure) {
      throw new IllegalArgumentException("Could not read avatar", failure);
    }
    return switch (contentType) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      default -> throw new IllegalArgumentException("Unsupported avatar type");
    };
  }

  record StoredAvatar(Path path, String contentType) {
    public byte[] readBytes() throws IOException {
      return java.nio.file.Files.readAllBytes(path);
    }
  }
}
