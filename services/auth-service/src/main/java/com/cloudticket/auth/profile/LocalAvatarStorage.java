package com.cloudticket.auth.profile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Local filesystem implementation used until an object store is configured. */
@Component
public class LocalAvatarStorage implements AvatarStorage {

  private final Path root;

  @Autowired
  public LocalAvatarStorage(
      @Value("${cloudticket.avatar-storage-dir:./data/avatars}") String root) {
    this(Path.of(root));
  }

  public LocalAvatarStorage(Path root) {
    this.root = root.toAbsolutePath().normalize();
    try {
      Files.createDirectories(this.root);
    } catch (IOException failure) {
      throw new IllegalStateException("Could not initialise avatar storage", failure);
    }
  }

  @Override
  public String save(UUID userId, MultipartFile file) {
    String extension = AvatarStorage.extension(file);
    String filename = UUID.randomUUID() + extension;
    Path target = root.resolve(filename).normalize();
    if (!target.getParent().equals(root)) {
      throw new IllegalArgumentException("Invalid avatar filename");
    }
    try (var input = file.getInputStream()) {
      Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
      return filename;
    } catch (IOException failure) {
      throw new IllegalArgumentException("Could not store avatar", failure);
    }
  }

  @Override
  public Optional<StoredAvatar> open(String filename) {
    if (filename == null || !filename.matches("^[0-9a-fA-F-]{36}\\.(jpg|png|webp)$")) {
      return Optional.empty();
    }
    Path path = root.resolve(filename).normalize();
    if (!path.getParent().equals(root) || !Files.isRegularFile(path)) return Optional.empty();
    String contentType = switch (filename.substring(filename.lastIndexOf('.') + 1)
        .toLowerCase(Locale.ROOT)) {
      case "jpg" -> "image/jpeg";
      case "png" -> "image/png";
      case "webp" -> "image/webp";
      default -> "application/octet-stream";
    };
    return Optional.of(new StoredAvatar(path, contentType));
  }

  @Override
  public void delete(String filename) {
    open(filename).ifPresent(stored -> {
      try {
        Files.deleteIfExists(stored.path());
      } catch (IOException ignored) {
        // Cleanup is best effort after a successful database update.
      }
    });
  }
}
