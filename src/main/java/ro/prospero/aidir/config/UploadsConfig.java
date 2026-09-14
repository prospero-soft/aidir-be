package ro.prospero.aidir.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.file.Path;

/**
 * Where uploaded media lives and how it is addressed from outside.
 *
 * <p>{@code root} is the one place on disk the application writes to, and everything stored under it is
 * recorded in the database as a path <em>relative to this root</em> - moving the root is then a matter of
 * pointing the property somewhere else and copying the tree, with no data migration.
 */
@AllArgsConstructor
@Getter
public class UploadsConfig {
    private Path root;
    private String publicBasePath;
}
