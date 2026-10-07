package com.cloudticket.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudticket.auth.persistence.entity.AuthUserEntity;
import com.cloudticket.auth.persistence.mapper.AuthUserMapper;
import com.cloudticket.auth.profile.AvatarStorage;
import com.cloudticket.auth.profile.LocalAvatarStorage;
import com.cloudticket.auth.service.AuthService;
import com.cloudticket.auth.security.TokenService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.List;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class ProfileServiceTest {

  private final AuthUserMapper users = mock(AuthUserMapper.class);
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
  private final AvatarStorage avatars = mock(AvatarStorage.class);
  private final TokenService tokens = mock(TokenService.class);

  @Test
  void nicknameIsTrimmedAndPersisted() {
    UUID id = UUID.randomUUID();
    AuthUserEntity user = user(id);
    when(users.selectById(id)).thenReturn(user);
    when(users.updateNickname(id, "New name")).thenReturn(1);
    when(users.selectProfile(id)).thenReturn(user);
    AuthService service = service();

    assertEquals(user, service.updateNickname(id, "  New name "));
    assertEquals("New name", user.getNickname());
    verify(users).updateNickname(id, "New name");
  }

  @Test
  void publicProfilesExposeOnlyTheRequestedUsers() {
    UUID id = UUID.randomUUID();
    AuthUserEntity user = user(id);
    user.setAvatarFilename("avatar.png");
    List<UUID> ids = List.of(id);
    when(users.selectPublicProfiles(ids)).thenReturn(List.of(user));

    assertEquals(List.of(user), service().publicProfiles(ids));
    verify(users).selectPublicProfiles(ids);
  }

  @Test
  void nicknameCannotExceedOneHundredTwentyCharacters() {
    UUID id = UUID.randomUUID();
    when(users.selectById(id)).thenReturn(user(id));
    AuthService service = service();

    assertThrows(IllegalArgumentException.class, () -> service.updateNickname(id, "x".repeat(121)));
  }

  @Test
  void passwordChangeRequiresTheCurrentPassword() {
    UUID id = UUID.randomUUID();
    when(users.selectById(id)).thenReturn(user(id));
    AuthService service = service();

    assertThrows(AuthService.InvalidCredentialsException.class,
        () -> service.changePassword(id, "WrongPass1", "NewStrong1"));
  }

  @Test
  void passwordChangeUsesTheExistingPolicyAndResetsTheHash() {
    UUID id = UUID.randomUUID();
    AuthUserEntity user = user(id);
    when(users.selectById(id)).thenReturn(user);
    AuthService service = service();

    service.changePassword(id, "StrongPass1", "NewStrong1");

    ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
    verify(users).resetPassword(eq(id), hash.capture());
    verify(tokens).revokeAllForUser(id);
    assertEquals(true, encoder.matches("NewStrong1", hash.getValue()));
  }

  @Test
  void avatarUploadRejectsUnsupportedContentType() {
    UUID id = UUID.randomUUID();
    when(users.selectById(id)).thenReturn(user(id));
    AuthService service = service();
    var file = new MockMultipartFile("file", "avatar.gif", "image/gif", new byte[] {1});

    assertThrows(IllegalArgumentException.class, () -> service.updateAvatar(id, file));
  }

  @Test
  void localAvatarStorageUsesGeneratedNamesAndRejectsOversizedFiles(@TempDir Path root) throws Exception {
    LocalAvatarStorage storage = new LocalAvatarStorage(root);
    byte[] png = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
    var file = new MockMultipartFile("file", "../../avatar.png", "image/png", png);

    String filename = storage.save(UUID.randomUUID(), file);

    assertFalse(filename.contains(".."));
    assertFalse(filename.contains("/"));
    assertEquals(png.length, Files.size(root.resolve(filename)));
    var huge = new MockMultipartFile("file", "huge.png", "image/png", new byte[2 * 1024 * 1024 + 1]);
    assertThrows(IllegalArgumentException.class, () -> storage.save(UUID.randomUUID(), huge));
  }

  private AuthService service() {
    return new AuthService(users, encoder, tokens, null, avatars);
  }

  private static AuthUserEntity user(UUID id) {
    AuthUserEntity user = new AuthUserEntity();
    user.setId(id);
    user.setPasswordHash(new BCryptPasswordEncoder().encode("StrongPass1"));
    user.setNickname("old");
    user.setStatus("ACTIVE");
    user.setScopeVersion(0L);
    return user;
  }

}
