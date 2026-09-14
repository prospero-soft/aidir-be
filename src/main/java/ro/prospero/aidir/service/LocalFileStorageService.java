package ro.prospero.aidir.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ro.prospero.aidir.config.UploadsConfig;
import ro.prospero.aidir.exception.ApiPayloadValidationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Files on the local filesystem, under {@code aidir.uploads.root}.
 *
 * <p>Swapping this for object storage is a matter of another {@link FileStorageService} implementation:
 * callers only ever see root-relative paths, which is all the database stores.
 */
@Service
public class LocalFileStorageService implements FileStorageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFileStorageService.class);

    /**
     * The extension is decided here, from the content type, and never taken from
     * {@code getOriginalFilename()} - a client-supplied name is free to contain "../" or a second
     * extension, and neither belongs in a path we build.
     */
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/webp", "webp",
            "image/gif", "gif",
            "image/svg+xml", "svg"
    );

    private final UploadsConfig uploadsConfig;

    public LocalFileStorageService(UploadsConfig uploadsConfig) {
        this.uploadsConfig = uploadsConfig;
    }

    @Override
    public String storeImage(MultipartFile file, String relativeDir, String baseName) {
        if (file == null || file.isEmpty()) {
            throw new ApiPayloadValidationException("Uploaded image is empty.",
                                                    List.of(baseName + ": the uploaded file is empty"));
        }

        String extension = extensionFor(file.getContentType(), baseName);
        String relativePath = relativeDir + "/" + baseName + "." + extension;
        Path target = resolveInsideRoot(relativePath);

        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store " + relativePath, e);
        }

        return relativePath;
    }

    @Override
    public void delete(String relativePath) {
        try {
            Files.deleteIfExists(resolveInsideRoot(relativePath));
        } catch (IOException e) {
            // Cleanup is best-effort: the caller is already unwinding a failed request, and an
            // orphaned file is a smaller problem than losing the original failure behind this one.
            LOGGER.warn("Could not delete {} while cleaning up", relativePath, e);
        }
    }

    private String extensionFor(String contentType, String baseName) {
        String normalised = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        String extension = EXTENSION_BY_CONTENT_TYPE.get(normalised);
        if (extension == null) {
            throw new ApiPayloadValidationException(
                    "Unsupported image type.",
                    List.of(baseName + ": expected one of " + EXTENSION_BY_CONTENT_TYPE.keySet()
                            + " but the file was sent as '" + normalised + "'"));
        }
        return extension;
    }

    /**
     * Belt and braces. Every component of the paths built here is ours, but resolving through the root
     * and checking the result is what makes that a property of the code rather than of the callers.
     */
    private Path resolveInsideRoot(String relativePath) {
        Path root = uploadsConfig.getRoot();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Refusing to touch " + relativePath + ", it escapes the uploads root");
        }
        return resolved;
    }
}
