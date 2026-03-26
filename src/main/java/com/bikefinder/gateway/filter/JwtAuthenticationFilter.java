package com.bikefinder.gateway.filter;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${auth.jwks.url}")
    private String jwksUrl;

    @Value("${auth.jwt.issuer}")
    private String expectedIssuer;

    @Value("${auth.jwt.audience}")
    private String expectedAudience;

    @Value("${gateway.public-paths}")
    private String publicPathsRaw;

    // 1. Instanciar el Matcher de Spring
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private List<String> publicPaths;

    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        loadPublicKey();

        this.publicPaths = Arrays.stream(publicPathsRaw.split(","))
                .map(String::trim)
                .toList();
        loadPublicKey();

        // TEMPORAL - borrar después
        log.info(">>> PUBLIC PATHS CARGADOS: {}", publicPaths);

        loadPublicKey(); // ← además tenés loadPublicKey() duplicado, llamala solo una vez
    }

    private void loadPublicKey() {
        try {
            log.info("Cargando clave pública desde JWKS: {}", jwksUrl);
            JWKSet jwkSet = JWKSet.load(new URL(jwksUrl));
            JWK jwk = jwkSet.getKeys().get(0);
            this.publicKey = ((RSAKey) jwk).toPublicKey();
            log.info("Clave pública cargada exitosamente");
        } catch (Exception e) {
            log.warn("No se pudo cargar JWKS al inicio, se reintentará: {}", e.getMessage());
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // ← Dejar pasar siempre los preflight requests de CORS
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // Rutas públicas no requieren autenticación
        if (isPublicPath(path)) {
            log.debug("Ruta pública, sin validación JWT: {}", path);
            return chain.filter(exchange);
        }

        // Extraer token
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Token no proporcionado para ruta protegida: {}", path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        try {
            // Validar y extraer claims
            if (publicKey == null) {
                loadPublicKey();
            }

            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(expectedIssuer)
                    .requireAudience(expectedAudience)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String email = claims.get("email", String.class);

            log.debug("JWT válido. userId={}, email={}, path={}", userId, email, path);

            // Agregar headers para los microservicios downstream
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Email", email != null ? email : "")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (JwtException e) {
            log.warn("Token inválido: {} - Path: {}", e.getMessage(), path);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        } catch (Exception e) {
            log.error("Error validando token", e);
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }

    // 3. Método simplificado y robusto
    private boolean isPublicPath(String path) {
        if (publicPaths == null) return false;

        boolean isPublic = publicPaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        log.debug(">>> PATH: {} - isPublic: {}", path, isPublic); // TEMPORAL

        return isPublic;
    }

    @Override
    public int getOrder() {
        return -100; // Ejecutar antes de otros filtros
    }
}
