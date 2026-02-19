package com.multiblockprojector.client.schematic;

import com.multiblockprojector.UniversalProjector;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Copies example .nbt files from JAR resources to the config schematics folder
 * on first launch. A .initialized marker file prevents re-copying.
 */
public class SchematicExampleCopier {

    private static final String RESOURCE_PREFIX = "/assets/multiblockprojector/examples/";
    private static final List<String> EXAMPLE_FILES = List.of(
        "test-furnace.nbt",
        "test-beacon.nbt",
        "test-tower.nbt",
        "test-wall.nbt"
    );

    /**
     * Copy example files if not already initialized.
     * Call this during client setup or before first GUI open.
     */
    public static void copyIfNeeded() {
        Path schematicsDir = FMLPaths.CONFIGDIR.get()
            .resolve("multiblockprojector").resolve("schematics");
        Path markerFile = schematicsDir.resolve(".initialized");

        if (Files.exists(markerFile)) {
            return; // Already initialized
        }

        Path examplesDir = schematicsDir.resolve("examples");
        try {
            Files.createDirectories(examplesDir);

            for (String fileName : EXAMPLE_FILES) {
                try (InputStream is = SchematicExampleCopier.class.getResourceAsStream(RESOURCE_PREFIX + fileName)) {
                    if (is != null) {
                        Files.copy(is, examplesDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            // Create marker file
            Files.createFile(markerFile);
            UniversalProjector.LOGGER.info("Copied example schematics to {}", examplesDir);
        } catch (IOException e) {
            UniversalProjector.LOGGER.warn("Failed to copy example schematics", e);
        }
    }
}
