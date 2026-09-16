package com.cloudticket.gateway;

import static org.junit.jupiter.api.Assertions.*;
import com.cloudticket.gateway.security.JwtTokenVerifier;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtAuthenticationFilterTest {
  @Test void acceptsSignedUnexpiredTokenAndExtractsTrustedClaims() throws Exception {
    String secret = "test-signing-key-with-enough-length";
    String token = sign(secret, "{\"sub\":\"u-1\",\"jti\":\"j-1\",\"roles\":[\"USER\"],\"permissions\":[\"order:read\"],\"scopes\":[\"ACTIVITY:a1\"],\"scopeVersion\":3,\"iat\":1,\"exp\":4102444800}");
    var claims = new JwtTokenVerifier(secret).verify(token);
    assertEquals("u-1", claims.subject());
    assertEquals("USER", claims.roles());
    assertEquals("order:read", claims.permissions());
    assertEquals("ACTIVITY:a1", claims.scopes());
    assertEquals("3", claims.scopeVersion());
  }
  @Test void rejectsExpiredAndTamperedTokens() throws Exception {
    String secret = "test-signing-key-with-enough-length";
    assertThrows(SecurityException.class, () -> new JwtTokenVerifier(secret).verify(sign(secret, "{\"sub\":\"u\",\"jti\":\"j\",\"iat\":1,\"exp\":2}")));
    assertThrows(SecurityException.class, () -> new JwtTokenVerifier(secret).verify("bad.token.value"));
  }
  private static String sign(String secret, String payload) throws Exception {
    String h = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
    String p = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return h + "." + p + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((h + "." + p).getBytes(StandardCharsets.UTF_8)));
  }
}
