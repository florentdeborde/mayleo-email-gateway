package com.florentdeborde.mayleo.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Component
public class SecurityRegistry {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final String[] SWAGGER_ROUTES = {
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/v3/api-docs"
    };

    private static final String ACTUATOR_ROUTE = "/actuator/**";

    private static final String[] API_ROUTES = {
            "/email-request/**",
            "/email-request"
    };

    @Value("${app.security.expose-swagger}")
    private boolean exposeSwagger;

    @Value("${app.security.expose-actuator}")
    private boolean exposeActuator;

    public boolean shouldSkipFilter(String path) {
        return Stream.of(
                        SWAGGER_ROUTES,
                        new String[]{ACTUATOR_ROUTE}
                )
                .flatMap(Stream::of)
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    public String[] getPublicRoutes() {
        List<String> publicRoutes = new ArrayList<>();

        if (exposeSwagger) {
            publicRoutes.addAll(List.of(SWAGGER_ROUTES));
        }

        if (exposeActuator) {
            publicRoutes.add(ACTUATOR_ROUTE);
        }

        return publicRoutes.toArray(new String[0]);
    }
}