package ro.prospero.aidir.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class StorageConfig {

    @Value("${aidir.uploads.root}")
    private String uploadsRoot;

    @Value("${aidir.uploads.public-base-path}")
    private String uploadsPublicBasePath;

    /**
     * The root is resolved to an absolute, normalised path here rather than at every call site: the
     * storage service compares stored paths against it to keep writes inside the tree, and a relative
     * root would make that check depend on the working directory.
     */
    @Bean
    public UploadsConfig uploadsConfig() {
        Path root = Paths.get(uploadsRoot).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create the uploads root at " + root, e);
        }
        return new UploadsConfig(root, uploadsPublicBasePath);
    }
}
