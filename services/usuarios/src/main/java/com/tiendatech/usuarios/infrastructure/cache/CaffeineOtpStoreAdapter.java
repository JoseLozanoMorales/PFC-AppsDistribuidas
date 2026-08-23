package com.tiendatech.usuarios.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.tiendatech.usuarios.domain.port.out.OtpStorePort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class CaffeineOtpStoreAdapter implements OtpStorePort {
    private final Cache<String, String> otpCache;
    private final Cache<String, Integer> attemptsCache;
    private final Cache<String, String> passwordChangeCache;

    public CaffeineOtpStoreAdapter(@Qualifier("otpCache") Cache<String, String> otpCache,
                                   @Qualifier("attemptsCache") Cache<String, Integer> attemptsCache,
                                   @Qualifier("pwdChangeCache") Cache<String, String> passwordChangeCache) {
        this.otpCache = otpCache;
        this.attemptsCache = attemptsCache;
        this.passwordChangeCache = passwordChangeCache;
    }

    public String findOtpHash(String key) { return otpCache.getIfPresent(key); }
    public void saveOtpHash(String key, String hash) { otpCache.put(key, hash); }
    public void removeOtp(String key) { otpCache.invalidate(key); }
    public Integer findAttemptCount(String key) { return attemptsCache.getIfPresent(key); }
    public void saveAttemptCount(String key, int count) { attemptsCache.put(key, count); }
    public String findPasswordChangeEmail(String token) { return passwordChangeCache.getIfPresent(token); }
    public void savePasswordChangeToken(String token, String email) { passwordChangeCache.put(token, email); }
    public void removePasswordChangeToken(String token) { passwordChangeCache.invalidate(token); }
}
