package io.commerce.api_gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Get userId from header (injected by JwtAuthFilter)
        // If not present (public route), use IP address instead
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        String rateLimitKey = userId != null
                ? "rate_limit:user:" + userId
                : "rate_limit:ip:" + clientIp;

        return redisTemplate.opsForValue()
                .increment(rateLimitKey)
                .flatMap(count -> {
                    // Set expiry only on first request (count == 1)
                    if (count == 1) {
                        return redisTemplate.expire(rateLimitKey, WINDOW)
                                .then(Mono.just(count));
                    }
                    return Mono.just(count);
                })
                .flatMap(count -> {
                    log.debug("Rate limit check — key: {}, count: {}/{}",
                            rateLimitKey, count, MAX_REQUESTS_PER_MINUTE);

                    if (count > MAX_REQUESTS_PER_MINUTE) {
                        log.warn("Rate limit exceeded — key: {}, count: {}",
                                rateLimitKey, count);
                        return rateLimitExceededResponse(exchange);
                    }

                    // Add rate limit headers to response
                    exchange.getResponse().getHeaders()
                            .add("X-RateLimit-Limit",
                                    String.valueOf(MAX_REQUESTS_PER_MINUTE));
                    exchange.getResponse().getHeaders()
                            .add("X-RateLimit-Remaining",
                                    String.valueOf(MAX_REQUESTS_PER_MINUTE - count));

                    return chain.filter(exchange);
                });
    }

    private Mono<Void> rateLimitExceededResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("X-RateLimit-Limit",
                String.valueOf(MAX_REQUESTS_PER_MINUTE));
        response.getHeaders().add("X-RateLimit-Remaining", "0");
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return 0; // Runs after JwtAuthFilter (which is -1)
    }
}
