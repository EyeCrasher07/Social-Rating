package dev.eyecrasher.socialrating;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

public final class RatingConfig {
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("socialrating/client.json");
    public boolean showNameplates = true;
    public boolean showInteractionHint = true;
    public int nameplateDistance = 32;
    public String nameplateMode = "number";
    public String language = "auto";

    public static RatingConfig load() {
        Path source = Files.exists(PATH) ? PATH : FabricLoader.getInstance().getConfigDir().resolve("socialrating.json");
        if (Files.exists(source)) {
            try (var reader = Files.newBufferedReader(source)) {
                var config = new GsonBuilder().create().fromJson(reader, RatingConfig.class);
                if (config != null) {
                    config.nameplateDistance = Math.clamp(config.nameplateDistance, 8, 64);
                    if (!"stars".equals(config.nameplateMode)) config.nameplateMode = "number";
                    if (config.language == null || !config.language.matches("[a-z0-9_]{1,32}")) config.language = "auto";
                    if (!Files.exists(PATH)) config.save();
                    return config;
                }
            } catch (IOException | RuntimeException error) {
                LoggerFactory.getLogger("socialrating").warn("Cannot read Social Rating configuration", error);
            }
        }
        var config = new RatingConfig();
        try { if (!Files.exists(PATH)) config.save(); }
        catch (IOException error) { LoggerFactory.getLogger("socialrating").warn("Cannot create Social Rating client configuration", error); }
        return config;
    }

    public void save() throws IOException {
        Files.createDirectories(PATH.getParent());
        Path temporary = Files.createTempFile(PATH.getParent(), "socialrating-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(this) + "\n");
            Files.move(temporary, PATH, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
