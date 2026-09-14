package ro.prospero.aidir.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Somewhere to put uploaded files that is not the database.
 *
 * <p>Every path this returns and accepts is relative to the storage root, never absolute: that is what
 * lets the backing store change - a different directory today, object storage later - without rewriting
 * the rows that point at it.
 */
public interface FileStorageService {

    /**
     * Store one image and return its root-relative path.
     *
     * @param file        the uploaded part
     * @param relativeDir directory beneath the root, e.g. {@code tool-submissions/42}
     * @param baseName    file name without an extension; the extension comes from the content type,
     *                    not from what the client called the file
     * @throws ro.prospero.aidir.exception.ApiPayloadValidationException if the part is empty or is not
     *                                                                   an image type we accept
     */
    String storeImage(MultipartFile file, String relativeDir, String baseName);

    /** Remove a stored file. Silent when it is already gone - callers use this to clean up after a failure. */
    void delete(String relativePath);
}
