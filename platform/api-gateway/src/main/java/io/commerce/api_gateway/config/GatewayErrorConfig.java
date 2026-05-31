/*
package io.commerce.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.*;

import java.time.Instant;
import java.util.Map;

@Configuration
public class GatewayErrorConfig {

    @Bean
    public RouterFunction<ServerResponse> gatewayRouterFunction() {
        return RouterFunctions.route(
                RequestPredicates.all(),
                request -> ServerResponse
                        .status(HttpStatus.NOT_FOUND)
                        .bodyValue(Map.of(
                                "success", false,
                                "timestamp", Instant.now().toString(),
                                "error", Map.of(
                                        "status", 404,
                                        "title", "Not Found",
                                        "detail", "Route not found: " + request.path()
                                )
                        ))
        );
    }
}
*/
