package com.florentdeborde.mayleo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class AltairBearerFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AltairBearerFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";
    private final String expectedToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AltairBearerFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/altair/") && !path.startsWith("/.well-known/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("[Altair] {} {} — rejected: missing or malformed Authorization header", request.getMethod(), request.getRequestURI());
            writeUnauthorized(response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        if (!expectedToken.equals(token)) {
            log.warn("[Altair] {} {} — rejected: invalid bearer token", request.getMethod(), request.getRequestURI());
            writeUnauthorized(response);
            return;
        }

        log.debug("[Altair] {} {} — authenticated", request.getMethod(), request.getRequestURI());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("altair", null, Collections.emptyList()));

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(Map.of("code", "UNAUTHORIZED", "message", "Invalid or missing bearer token"))
        );
    }
}
