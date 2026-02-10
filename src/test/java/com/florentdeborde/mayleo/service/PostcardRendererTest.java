package com.florentdeborde.mayleo.service;

import com.florentdeborde.mayleo.model.EmailRequest;
import com.florentdeborde.mayleo.dto.internal.PostcardHtml;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Unit Test - PostcardRenderer")
class PostcardRendererTest {

    private PostcardRenderer postcardRenderer;

    @BeforeEach
    void setUp() {
        postcardRenderer = new PostcardRenderer();
    }

    @Test
    @DisplayName("✅ render: Should successfully assemble HTML with valid image path")
    void render_Success() {
        // GIVEN
        EmailRequest request = EmailRequest.builder()
                .id("test-req")
                .message("Hello World")
                .imagePath("postcards/postcard-1.jpg")
                .build();

        // WHEN
        PostcardHtml result = postcardRenderer.render(request, "Small Note");

        // THEN
        assertNotNull(result);
        assertNotNull(result.getPostcard());
        assertTrue(result.getHtml().contains("cid:postcardImage"));
        assertTrue(result.getHtml().contains("Hello World"));
        assertTrue(result.getHtml().contains("Small Note"));
        assertEquals("postcards/postcard-1.jpg", result.getPostcard().getFilename());
    }

    @Test
    @DisplayName("✅ render: Should use random fallback when imagePath is invalid")
    void render_FallbackWhenImageNotFound() {
        // GIVEN
        EmailRequest request = EmailRequest.builder()
                .id("test-fallback")
                .imagePath("invalid/path.jpg")
                .build();

        // WHEN
        PostcardHtml result = postcardRenderer.render(request, "Note");

        // THEN
        assertNotNull(result);
        assertTrue(result.getPostcard().getFilename().startsWith("postcards/postcard-"));
        assertTrue(result.getPostcard().getFilename().endsWith(".jpg"));
    }

    @Test
    @DisplayName("✅ render: Should handle null message gracefully")
    void render_NullMessage() {
        // GIVEN
        EmailRequest request = EmailRequest.builder()
                .id("test-null")
                .subject("subject")
                .message(null)
                .build();

        // WHEN
        PostcardHtml result = postcardRenderer.render(request, "Note");

        // THEN
        assertNotNull(result);
        assertFalse(result.getHtml().contains("null"));
    }

    @Test
    @DisplayName("✅ cache: Should populate and reuse orientation and template caches")
    void render_CacheReusage() {
        // GIVEN
        EmailRequest request = EmailRequest.builder()
                .id("req-1")
                .imagePath("postcards/postcard-1.jpg")
                .build();

        // WHEN
        PostcardHtml result1 = postcardRenderer.render(request, "Note");
        PostcardHtml result2 = postcardRenderer.render(request, "Note");

        // THEN
        assertEquals(result1.getPostcard().isLandscape(), result2.getPostcard().isLandscape());
        assertEquals(result1.getHtml(), result2.getHtml());
    }

    @Test
    @DisplayName("♻ invalidateTemplateCache: Should clear the template map")
    void invalidateTemplateCache_ShouldClearMap() {
        // GIVEN
        Map<String, String> cache = (Map<String, String>) ReflectionTestUtils.getField(postcardRenderer, "templateCache");
        cache.put("templates/postcard-email-landscape.html", "<html>Mock</html>");

        assertFalse(cache.isEmpty());

        // WHEN
        postcardRenderer.invalidateTemplateCache();

        // THEN
        assertTrue(cache.isEmpty(), "Template cache should be empty after invalidating");
    }

    @Test
    @DisplayName("♻ invalidateOrientationImageCache: Should remove specific image from cache")
    void invalidateOrientationImageCache_ShouldRemoveSpecificKey() {
        // GIVEN
        Map<String, Boolean> cache = (Map<String, Boolean>) ReflectionTestUtils.getField(postcardRenderer, "imageOrientationCache");
        String imgPath = "postcards/postcard-0.jpg";
        cache.put(imgPath, true);

        assertTrue(cache.containsKey(imgPath));

        // WHEN
        postcardRenderer.invalidateOrientationImageCache(imgPath);

        // THEN
        assertFalse(cache.containsKey(imgPath), "Orientation image cache should be empty after invalidating");
    }
}