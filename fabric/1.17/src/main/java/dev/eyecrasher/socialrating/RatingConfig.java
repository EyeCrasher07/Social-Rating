package dev.eyecrasher.socialrating;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import dev.eyecrasher.socialrating.CompatLogger;

public final class RatingConfig {
    private static final Path PATH = RatingPlatform.configDirectory().resolve("socialrating/client.json");
    public boolean showNameplates = true;
    public boolean showInteractionHint = true;
    public int nameplateDistance = 32;
    public String nameplateMode = "number";
    public String language = "auto";

    public static RatingConfig load() {
        Path source = Files.exists(PATH) ? PATH : RatingPlatform.configDirectory().resolve("socialrating.json");
        if (Files.exists(source)) {
            try (var reader = Files.newBufferedReader(source)) {
                var config = new GsonBuilder().create().fromJson(reader, RatingConfig.class);
                if (config != null) {
                    config.nameplateDistance = dev.eyecrasher.socialrating.JavaCompat.clamp(config.nameplateDistance, 8, 64);
                    if (!"stars".equals(config.nameplateMode)) config.nameplateMode = "number";
                    if (config.language == null || !config.language.matches("[a-z0-9_]{1,32}")) config.language = "auto";
                    if (!Files.exists(PATH)) config.save();
                    return config;
                }
            } catch (IOException | RuntimeException error) {
                CompatLogger.getLogger("socialrating").warn("Cannot read Social Rating configuration", error);
            }
        }
        var config = new RatingConfig();
        try { if (!Files.exists(PATH)) config.save(); }
        catch (IOException error) { CompatLogger.getLogger("socialrating").warn("Cannot create Social Rating client configuration", error); }
        return config;
    }

    public void save() throws IOException {
        Files.createDirectories(PATH.getParent());
        Path temporary = Files.createTempFile(PATH.getParent(), "socialrating-", ".tmp");
        try {
            dev.eyecrasher.socialrating.JavaCompat.write(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(this) + "\n");
            Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
