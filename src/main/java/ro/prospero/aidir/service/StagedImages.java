package ro.prospero.aidir.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Images stored ahead of the transaction that inserts the row referring to them, which cannot be inserted
 * without them, and removed again if that transaction fails. Their directory is therefore a fresh random one
 * rather than the row's id, which does not exist yet.
 */
final class StagedImages {
    private final FileStorageService fileStorageService;
    private final String dir;
    private final List<String> stored = new ArrayList<>();

    /** @param area the uploads directory the row's kind keeps its images in */
    StagedImages(FileStorageService fileStorageService, String area) {
        this.fileStorageService = fileStorageService;
        this.dir = area + "/" + UUID.randomUUID();
    }

    /** A random suffix, so a URL guessed from one row's image names leads nowhere in another's. */
    String store(MultipartFile image, String baseName) {
        String path = fileStorageService.storeImage(image, dir,
                                                    baseName + "-" + UUID.randomUUID().toString().substring(0, 8));
        stored.add(path);
        return path;
    }

    void discard() {
        stored.forEach(fileStorageService::delete);
    }
}
