package com.tiendatech.usuarios.domain.port.out;

public interface OtpStorePort {
    String findOtpHash(String key);
    void saveOtpHash(String key, String hash);
    void removeOtp(String key);
    Integer findAttemptCount(String key);
    void saveAttemptCount(String key, int count);
    String findPasswordChangeEmail(String token);
    void savePasswordChangeToken(String token, String email);
    void removePasswordChangeToken(String token);
}
