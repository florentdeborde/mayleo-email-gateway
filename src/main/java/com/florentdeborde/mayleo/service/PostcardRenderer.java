package com.florentdeborde.mayleo.service;

import com.florentdeborde.mayleo.model.EmailRequest;
import com.florentdeborde.mayleo.dto.internal.Postcard;
import com.florentdeborde.mayleo.dto.internal.PostcardHtml;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PostcardRenderer {

    private final Map<String, String> templateCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> imageOrientationCache = new ConcurrentHashMap<>();
    private final Random random = new Random();


    public PostcardHtml render(EmailRequest request, String smallNote) {
        try {
            String mainText = request.getMessage();
            Postcard postcard = resolvePostcard(request);

            String templatePath = postcard.isLandscape() ?
                    "templates/postcard-email-landscape.html" :
                    "templates/postcard-email-portrait.html";

            String template = loadTemplate(templatePath, request.getId());
            template = template.replace("{{imageUrl}}", "cid:postcardImage");
            template = template.replace("{{mainText}}", mainText != null ? mainText : "");
            template = template.replace("{{smallNote}}", smallNote != null ? smallNote : "");

            return new PostcardHtml(template, postcard);

        } catch (Exception e) {
            throw new RuntimeException("[%s] Failed to load email template: %s".formatted(request.getId(), e.getMessage()));
        }
    }

    private Postcard resolvePostcard(EmailRequest request) throws IOException {
        // TODO: Implement CLIENT_STORAGE (DEFAULT only for now)

        String localPath = "";
        String filename = null;

        if (request.getImagePath() != null && !request.getImagePath().isBlank()) {
            String imagePath = request.getImagePath();
            if (imagePath.startsWith("/")) imagePath = imagePath.substring(1);
            String targetPath = localPath + imagePath;

            if (new ClassPathResource(targetPath).exists()) {
                filename = targetPath;
            } else {
                log.warn("[{}] Requested image not found: {}. Falling back to random image.", request.getId(), targetPath);
            }
        }

        if (filename == null) {
            int randomIndex = random.nextInt(9);
            filename = localPath + "postcards/postcard-" + randomIndex + ".jpg";
        }

        if (imageOrientationCache.containsKey(filename)) {
            return new Postcard(filename, imageOrientationCache.get(filename));
        }

        ClassPathResource imageResource = new ClassPathResource(filename);
        if (imageResource.exists()) {
            log.info("[{}] Decoding image metadata from disk: {}", request.getId(), filename);
            BufferedImage img = ImageIO.read(imageResource.getInputStream());
            boolean isLandscape = img.getWidth() >= img.getHeight();

            imageOrientationCache.put(filename, isLandscape);
            return new Postcard(filename, isLandscape);
        }

        throw new IOException("[%s] Image not found: %s".formatted(request.getId(), filename));
    }

    private String loadTemplate(String templatePath, String requestId) {
        return templateCache.computeIfAbsent(templatePath, path -> {
            try {
                log.info("[{}] (First access) Loading template from disk to cache: {}", requestId, path);
                ClassPathResource resource = new ClassPathResource(path);
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                }
            } catch (IOException e) {
                throw new RuntimeException("[%s] (First access) Failed to read template: %s".formatted(requestId, path));
            }
        });
    }

    public void invalidateOrientationImageCache(String filename) {
        imageOrientationCache.remove(filename);
    }

    public void invalidateTemplateCache() {
        templateCache.clear();
    }
}