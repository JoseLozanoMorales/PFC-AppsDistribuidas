package com.tiendatech.usuarios.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class CacheConfig {

    @Value("${otp.ttl-minutes:10}")
    private int otpTtlMin;

    @Value("${password-change.ttl-minutes:30}")
    private int passwordChangeTtlMin;

    @Bean("otpCache")
    public Cache<String, String> otpCache() {
        // Guarda el HASH del OTP por clave email:txId
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(otpTtlMin))
                .maximumSize(50_000)
                .build();
    }

    @Bean("attemptsCache")
    public Cache<String, Integer> attemptsCache() {
        // Rate limit simple por 15 minutos
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(15))
                .maximumSize(100_000)
                .build();
    }

    @Bean("pwdChangeCache")
    public Cache<String, String> pwdChangeCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(passwordChangeTtlMin))
                .maximumSize(50_000)
                .build();
    }
}
