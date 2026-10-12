package ro.prospero.aidir.service;

import org.springframework.stereotype.Component;
import ro.prospero.aidir.config.UploadsConfig;

/**
 * Turns a stored path, relative to the uploads root, into the URL the file is served at. What comes back
 * addresses the file as-is, so no caller has to know where media is mounted.
 */
@Component
public class MediaUrls {
    private final UploadsConfig uploadsConfig;

    public MediaUrls(UploadsConfig uploadsConfig) {
        this.uploadsConfig = uploadsConfig;
    }

    public String publicUrl(String relativePath) {
        String base = uploadsConfig.getPublicBasePath();
        String path = relativePath.replace('\\', '/');
        if (base.endsWith("/") || path.startsWith("/")) {
            return base + path;
        }
        return base + "/" + path;
    }
}
