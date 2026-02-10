package com.florentdeborde.mayleo.security;

import com.florentdeborde.mayleo.model.ApiClient;
import com.florentdeborde.mayleo.repository.ApiClientRepository;
import com.florentdeborde.mayleo.service.HmacService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test - SecurityFilter")
class SecurityFilterTest {

    @Mock private ApiClientRepository apiClientRepository;
    @Mock private HmacService hmacService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;
    @Mock private PrintWriter writer;

    private SecurityFilter securityFilter;
    private final String PLAIN_API_KEY = "MAYLEO_API_KEY";
    private String hashedApiKey;
    private final String TEST_SALT = "test-salt-secret";

    @BeforeEach
    void setUp() {
        SecurityRegistry securityRegistry = new SecurityRegistry();
        securityFilter = new SecurityFilter(apiClientRepository, securityRegistry, hmacService, TEST_SALT, true);
        hashedApiKey = ApiKeyEncoder.hashSha256(PLAIN_API_KEY, TEST_SALT);
    }

    // --- PHASE 0 : EXCLUSION ---

    @Test
    @DisplayName("✅ should skip filtering for public routes")
    void shouldSkipFilteringForPublicRoutes() {
        when(request.getRequestURI()).thenReturn("/swagger-ui/index.html");
        assertTrue(securityFilter.shouldNotFilter(request));

        when(request.getRequestURI()).thenReturn("/api/v1/emails");
        assertFalse(securityFilter.shouldNotFilter(request));
    }

    // --- PHASE 1 : AUTHENTICATION & ORIGIN ---

    @Test
    @DisplayName("❌ should block if API Key is missing")
    void shouldBlockWhenApiKeyMissing() throws Exception {
        when(request.getHeader("X-API-KEY")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(response.getWriter()).thenReturn(writer);

        securityFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("❌ should block if API Key is invalid (not in DB)")
    void shouldBlockWhenApiKeyInvalid() throws Exception {
        when(request.getHeader("X-API-KEY")).thenReturn("WRONG_KEY");
        when(apiClientRepository.findByApiKeyWithDomains(anyString())).thenReturn(Optional.empty());
        when(response.getWriter()).thenReturn(writer);

        securityFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("❌ should block when client account is disabled")
    void shouldBlockWhenClientIsDisabled() throws Exception {
        ApiClient disabledClient = ApiClient.builder().enabled(false).build();
        when(request.getHeader("X-API-KEY")).thenReturn(PLAIN_API_KEY);
        when(apiClientRepository.findByApiKeyWithDomains(hashedApiKey)).thenReturn(Optional.of(disabledClient));
        when(response.getWriter()).thenReturn(writer);

        securityFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    @Test
    @DisplayName("❌ should block if Origin is invalid")
    void shouldBlockWhenOriginIsInvalid() throws Exception {
        ApiClient client = ApiClient.builder()
                .apiKey(hashedApiKey).enabled(true)
                .allowedDomains(Set.of("https://authorized.com")).build();

        when(request.getHeader("X-API-KEY")).thenReturn(PLAIN_API_KEY);
        when(request.getHeader("Origin")).thenReturn("https://hacker.com");
        when(apiClientRepository.findByApiKeyWithDomains(hashedApiKey)).thenReturn(Optional.of(client));
        when(response.getWriter()).thenReturn(writer);

        securityFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
    }

    // --- PHASE 2 : PAYLOAD SIZE ---

    @Test
    @DisplayName("❌ should return 413 when Content-Length header is too large")
    void shouldBlockWhenHeaderContentLengthIsTooLarge() throws Exception {
        setupValidAuthMock();
        when(request.getContentLengthLong()).thenReturn((long) SecurityFilter.MAX_BODY_SIZE + 1);
        when(response.getWriter()).thenReturn(writer);

        securityFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    }

    @Test
    @DisplayName("❌ should return 413 when actual body stream exceeds MAX_BODY_SIZE")
    void shouldBlockWhenActualBodyIsTooLarge() throws Exception {
        setupValidAuthMock();
        when(request.getContentLengthLong()).thenReturn(100L);
        when(response.getWriter()).thenReturn(writer);

        // Simuler une IOException (jetée par CachedBodyHttpServletRequest quand le stream dépasse la limite)
        when(request.getInputStream()).thenThrow(new IOException("Limit exceeded"));

        securityFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
    }

    // --- PHASE 3 : HMAC ---

    @Test
    @DisplayName("❌ should return 401 when HMAC signature is invalid")
    void shouldReturn401WhenHmacIsInvalid() throws Exception {
        ApiClient client = setupValidAuthMock();
        mockRequest("{}", "wrong-sig", "https://authorized.com", "POST");
        when(response.getWriter()).thenReturn(writer);
        when(hmacService.verifySignature(any(), anyString(), anyString())).thenReturn(false);

        securityFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(writer).write(contains("INVALID_SIGNATURE"));
    }

    // --- PHASE 4 & 5 : SUCCESS ---

    @Test
    @DisplayName("✅ should allow request when all checks pass")
    void shouldAllowWhenAllSecurityChecksPass() throws Exception {
        ApiClient client = setupValidAuthMock();
        String json = "{\"to\":\"test@test.com\"}";
        mockRequest(json, "valid-sig", "https://authorized.com", "POST");
        when(hmacService.verifySignature(any(), eq("valid-sig"), anyString())).thenReturn(true);

        securityFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(any(), any());
        verify(request).setAttribute(eq("authenticatedClient"), eq(client));
    }

    // --- UTILS (IP & HELPERS) ---

    @Test
    @DisplayName("✅ should correctly anonymize different IP formats")
    void shouldAnonymizeIp() {
        assertEquals("192.168.1.xxx", ReflectionTestUtils.invokeMethod(securityFilter, "anonymizeIp", "192.168.1.50"));
        assertEquals("2001:0db8:xxxx", ReflectionTestUtils.invokeMethod(securityFilter, "anonymizeIp", "2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
        assertEquals("unknown", ReflectionTestUtils.invokeMethod(securityFilter, "anonymizeIp", ""));
        assertEquals("invalid-ip", ReflectionTestUtils.invokeMethod(securityFilter, "anonymizeIp", "not-an-ip"));
    }

    /**
     * Helper pour configurer un client valide en Phase 1
     */
    private ApiClient setupValidAuthMock() {
        ApiClient client = ApiClient.builder()
                .name("Test Client").apiKey(hashedApiKey).enabled(true)
                .hmacSecretKey("secret").allowedDomains(Set.of("https://authorized.com")).build();

        lenient().when(request.getHeader("X-API-KEY")).thenReturn(PLAIN_API_KEY);
        lenient().when(request.getHeader("Origin")).thenReturn("https://authorized.com");
        lenient().when(apiClientRepository.findByApiKeyWithDomains(hashedApiKey)).thenReturn(Optional.of(client));
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        return client;
    }

    private void mockRequest(String body, String signature, String origin, String method) throws IOException {
        lenient().when(request.getHeader("X-SIGNATURE")).thenReturn(signature);
        lenient().when(request.getMethod()).thenReturn(method);
        lenient().when(request.getContentLengthLong()).thenReturn((long) body.getBytes().length);
        ServletInputStream inputStream = new DelegatingServletInputStream(new ByteArrayInputStream(body.getBytes()));
        lenient().when(request.getInputStream()).thenReturn(inputStream);
    }
}