package com.bikefinder.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class RateLimitLoggingFilter implements GlobalFilter, Ordered {

    private static final String HEADER_REMAINING  = "X-RateLimit-Remaining";
    private static final String HEADER_REPLENISH  = "X-RateLimit-Replenish-Rate";
    private static final String HEADER_BURST      = "X-RateLimit-Burst-Capacity";
    private static final String HEADER_REQUESTED  = "X-RateLimit-Requested-Tokens";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers  = exchange.getResponse().getHeaders();
            HttpStatus  status   = (HttpStatus) exchange.getResponse().getStatusCode();
            String      path     = exchange.getRequest().getURI().getPath();
            String      userId   = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            String      ip       = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";

            String remaining = headers.getFirst(HEADER_REMAINING);
            String replenish = headers.getFirst(HEADER_REPLENISH);
            String burst     = headers.getFirst(HEADER_BURST);

            // Request bloqueada por rate limit
            if (HttpStatus.TOO_MANY_REQUESTS.equals(status)) {
                log.warn("[RATE-LIMIT] BLOCKED | path={} | userId={} | ip={} | remaining={} | replenishRate={} | burstCapacity={}",
                        path, userId != null ? userId : "anonymous", ip,
                        remaining, replenish, burst);
                return;
            }

            // Log normal (solo si remaining es bajo o DEBUG activo)
            if (remaining != null) {
                int rem = parseOrDefault(remaining, Integer.MAX_VALUE);
                int bur = parseOrDefault(burst, 0);

                if (rem <= (bur * 0.2)) {
                    // Alerta cuando queda ≤20% de capacidad
                    log.warn("[RATE-LIMIT] LOW-QUOTA | path={} | userId={} | ip={} | remaining={}/{}",
                            path, userId != null ? userId : "anonymous", ip, remaining, burst);
                } else {
                    log.debug("[RATE-LIMIT] OK | path={} | userId={} | remaining={}/{} | replenishRate={}",
                            path, remaining, burst, replenish);
                }
            }
        }));
    }

    private int parseOrDefault(String value, int defaultVal) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    @Override
    public int getOrder() {
        // Después de JwtAuthenticationFilter (-100) y después de RateLimiter
        return Ordered.LOWEST_PRECEDENCE;
    }
}
