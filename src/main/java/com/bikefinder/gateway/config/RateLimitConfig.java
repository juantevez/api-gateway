package com.bikefinder.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {

    /**
     * Limita por usuario autenticado (X-User-Id).
     * Fallback a IP si no hay usuario.
     * Este resolver se usa en rutas protegidas.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            // Fallback a IP
            return Mono.just("ip:" + exchange.getRequest()
                    .getRemoteAddress()
                    .getAddress()
                    .getHostAddress());
        };
    }

    /**
     * Limita por IP.
     * Se usa en rutas públicas como /auth/**.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
                exchange.getRequest()
                        .getRemoteAddress()
                        .getAddress()
                        .getHostAddress()
        );
    }

    /**
     * Rate limiter general para rutas de negocio.
     * 20 req/seg, burst de 40.
     */
    @Bean
    public RedisRateLimiter defaultRateLimiter() {
        return new RedisRateLimiter(20, 40, 1);
    }

    /**
     * Rate limiter estricto para /auth/**.
     * Previene brute force en login.
     * 5 req/seg, burst de 10.
     */
    @Bean
    public RedisRateLimiter authRateLimiter() {
        return new RedisRateLimiter(5, 10, 1);
    }
}
