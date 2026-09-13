package com.cloudticket.auth.service;

import com.cloudticket.auth.domain.UserEntity;
import com.cloudticket.auth.repository.UserRepository;
import com.cloudticket.auth.security.PasswordPolicy;
import com.cloudticket.auth.security.TokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private final UserRepository users; private final PasswordEncoder encoder; private final TokenService tokens;
    private final ConcurrentHashMap<String, UUID> resetTokens = new ConcurrentHashMap<>();
    public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens) { this.users=users; this.encoder=encoder; this.tokens=tokens; }
    public UserEntity register(String phone, String email, String password, String nickname) {
        phone=normalizePhone(phone); email=normalizeEmail(email); PasswordPolicy.requireValid(password);
        if (phone == null && email == null) throw new IllegalArgumentException("phone or email required");
        if (phone != null && users.existsByPhone(phone) || email != null && users.existsByEmail(email)) throw new DuplicateCredentialException();
        Instant now=Instant.now(); UserEntity u=new UserEntity(UUID.randomUUID(),phone,email,encoder.encode(password),nickname,"ACTIVE",0,null,0,now,now); return users.save(u);
    }
    public TokenService.Issued login(String identifier, String password) {
        String normalized = identifier != null && identifier.contains("@") ? normalizeEmail(identifier) : normalizePhone(identifier);
        UserEntity u=users.findByPhoneOrEmail(normalized).orElseThrow(() -> new InvalidCredentialsException());
        if (u.lockedUntil()!=null && u.lockedUntil().isAfter(Instant.now())) throw new InvalidCredentialsException();
        if (!encoder.matches(password, u.passwordHash())) {
            int count=u.failedLoginCount()+1; Instant lock=count>=MAX_FAILURES?Instant.now().plus(LOCK_DURATION):u.lockedUntil();
            users.save(new UserEntity(u.id(),u.phone(),u.email(),u.passwordHash(),u.nickname(),u.status(),count,lock,u.scopeVersion(),u.createdAt(),Instant.now()));
            throw new InvalidCredentialsException();
        }
        if (u.failedLoginCount()!=0 || u.lockedUntil()!=null) u=users.save(new UserEntity(u.id(),u.phone(),u.email(),u.passwordHash(),u.nickname(),u.status(),0,null,u.scopeVersion(),u.createdAt(),Instant.now()));
        return tokens.issue(u);
    }
    public TokenService.Issued refresh(String refreshToken) { var t=tokens.find(refreshToken); return tokens.rotate(refreshToken, users.findById(t.userId()).orElseThrow(InvalidCredentialsException::new)); }
    public void logout(String refreshToken) { tokens.revoke(refreshToken); }
    public String forgotPassword(String identifier) { String n=identifier!=null&&identifier.contains("@")?normalizeEmail(identifier):normalizePhone(identifier); users.findByPhoneOrEmail(n).ifPresent(u->resetTokens.put(UUID.randomUUID().toString(),u.id())); return "If the account exists, reset instructions were sent"; }
    public UserEntity resetPassword(String token,String password) { PasswordPolicy.requireValid(password); UUID id=resetTokens.remove(token); if(id==null) throw new InvalidCredentialsException(); UserEntity u=users.findById(id).orElseThrow(InvalidCredentialsException::new); return users.save(new UserEntity(u.id(),u.phone(),u.email(),encoder.encode(password),u.nickname(),u.status(),0,null,u.scopeVersion()+1,u.createdAt(),Instant.now())); }
    public UserEntity me(UUID userId) { return users.findById(userId).orElseThrow(InvalidCredentialsException::new); }
    public static String normalizeEmail(String e){ return e==null?null:e.trim().toLowerCase(Locale.ROOT); }
    public static String normalizePhone(String p){ if(p==null)return null; String n=p.replaceAll("[^0-9+]",""); return n.isBlank()?null:n; }
    public static class DuplicateCredentialException extends RuntimeException {}
    public static class InvalidCredentialsException extends RuntimeException {}
}
