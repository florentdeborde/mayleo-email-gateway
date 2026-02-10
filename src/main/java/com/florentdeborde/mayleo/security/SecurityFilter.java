package com.florentdeborde.mayleo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.florentdeborde.mayleo.dto.response.ErrorResponse;
import com.florentdeborde.mayleo.exception.ExceptionCode;
import com.florentdeborde.mayleo.model.ApiClient;
import com.florentdeborde.mayleo.repository.ApiClientRepository;
import com.florentdeborde.mayleo.service.HmacService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
public class SecurityFilter extends OncePerRequestFilter {

    public static final int MAX_BODY_SIZE = 2 * 1024 * 1024;

    private final ApiClientRepository apiClientRepository;
    private final SecurityRegistry securityRegistry;
    private final HmacService hmacService;
    private final String salt;
    private final boolean isHmacEnabled;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityFilter(ApiClientRepository apiClientRepository, SecurityRegistry securityRegistry, HmacService hmacService, String salt, boolean isHmacEnabled) {
        this.apiClientRepository = apiClientRepository;
        this.securityRegistry = securityRegistry;
        this.hmacService = hmacService;
        this.salt = salt;
        this.isHmacEnabled = isHmacEnabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return securityRegistry.shouldSkipFilter(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // --- PHASE 1 : HEADERS ---
        String plainApiKey = request.getHeader("X-API-KEY");
        String origin = request.getHeader("Origin");
        String uri = request.getServletPath();
        String maskedIp = anonymizeIp(request.getRemoteAddr());

        // Check API Key Presence
        if (plainApiKey == null || plainApiKey.isBlank()) {
            // Perform a dummy hash to ensure response time remains consistent
            // and prevent timing attacks from identifying valid vs invalid requests.
            ApiKeyEncoder.hashSha256("dummy-key", salt);
            log.warn("[Security Alert] Missing API Key | Masked IP: {} | Path: {}", maskedIp, uri);
            setErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ExceptionCode.INCORRECT_API_KEY);
            return;
        }

        // Load Client & Check API Key
        String hashedApiKey = ApiKeyEncoder.hashSha256(plainApiKey, salt);
        ApiClient client = apiClientRepository.findByApiKeyWithDomains(hashedApiKey).orElse(null);
        if (client == null) {
            log.warn("[Security Alert] Invalid API Key attempt | Masked IP: {} | Path: {}", maskedIp, uri);
            setErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ExceptionCode.INCORRECT_API_KEY);
            return;
        }
        if (!client.isEnabled()) {
            log.warn("[Security Alert] Attempt from disabled client: {} | Masked IP: {}", client.getName(), maskedIp);
            setErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ExceptionCode.CLIENT_DISABLED);
            return;
        }

        // Validate Origin
        String normalizedOrigin = (origin != null) ? origin.replaceAll("/$", "") : null;
        if (normalizedOrigin == null || client.getAllowedDomains() == null || !client.getAllowedDomains().contains(normalizedOrigin)) {
            log.warn("[Security Alert] Origin mismatch | Client: {} | Origin: {} | Masked IP: {}", client.getName(), origin, maskedIp);
            setErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, ExceptionCode.INVALID_ORIGIN);
            return;
        }

        // --- PHASE 2 : PAYLOAD SIZE ---
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_BODY_SIZE) {
            log.warn("[Security Alert] Payload too large");
            setErrorResponse(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, ExceptionCode.PAYLOAD_TOO_LARGE);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest;
        try {
            wrappedRequest = new CachedBodyHttpServletRequest(request, MAX_BODY_SIZE);
        } catch (IOException e) {
            log.warn("[Security Alert] Payload too large (Streaming) | IP: {}", anonymizeIp(request.getRemoteAddr()));
            setErrorResponse(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, ExceptionCode.PAYLOAD_TOO_LARGE);
            return;
        }

        // --- PHASE 3 : HMAC SIGNATURE (only for POST/PUT with body) ---
        String clientSignature = wrappedRequest.getHeader("X-SIGNATURE");
        if (isHmacEnabled && ("POST".equalsIgnoreCase(wrappedRequest.getMethod()) || "PUT".equalsIgnoreCase(wrappedRequest.getMethod()))) {

            // We use StreamUtils to read the input stream. This action populates the
            // ContentCachingRequestWrapper's internal cache, making the body available
            // for hmacService AND later for Spring's @RequestBody.
            // byte[] body = StreamUtils.copyToByteArray(wrappedRequest.getInputStream());
            byte[] body = wrappedRequest.getBody();

            if (clientSignature == null || !hmacService.verifySignature(body, clientSignature, client.getHmacSecretKey())) {
                log.warn("[Security Alert] Invalid HMAC Signature | Client: {} | Path: {}", client.getName(), uri);
                setErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, ExceptionCode.INVALID_SIGNATURE);
                return;
            }
        }

        // --- PHASE 4 : Populate Spring Security Context ---
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                client,
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // --- PHASE 5 : Forward the WRAPPED request to the next filter/controller ---
        wrappedRequest.setAttribute("authenticatedClient", client);
        filterChain.doFilter(wrappedRequest, response);
    }

    private void setErrorResponse(HttpServletResponse response, int status, ExceptionCode exCode) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        ErrorResponse error = new ErrorResponse(exCode.name(), exCode.getDefaultMessage());
        String json = objectMapper.writeValueAsString(error);

        response.getWriter().write(json);
    }

    private String anonymizeIp(String ip) {
        if (ip == null || ip.isBlank()) return "unknown";
        if (ip.contains(".")) { // IPv4
            return ip.substring(0, ip.lastIndexOf(".") + 1) + "xxx";
        } else if (ip.contains(":")) { // IPv6
            return ip.substring(0, ip.indexOf(":", ip.indexOf(":") + 1) + 1) + "xxxx";
        }
        return "invalid-ip";
    }
}