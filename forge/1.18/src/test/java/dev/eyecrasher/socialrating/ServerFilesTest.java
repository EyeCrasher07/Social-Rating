package dev.eyecrasher.socialrating;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static dev.eyecrasher.socialrating.RatingData.*;

public final class ServerFilesTest {
    private static final Gson JSON = new Gson();
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("socialrating-files-test-");
        try { run(root); System.out.println("PASS: " + checks + " configuration, translation and migration checks"); }
        finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void run(Path root) throws Exception {
        Path config = root.resolve("config");
        var files = new ServerFiles(config, null);
        check(Files.isRegularFile(config.resolve("socialrating/database.json")), "Database must be created in the mod config directory");
        check(Files.isRegularFile(files.directory.resolve("server.json")), "Server settings must be created");
        check(Files.isRegularFile(files.directory.resolve("lang/en_us.json")) && Files.isRegularFile(files.directory.resolve("lang/ru_ru.json")), "Bundled languages must be exported");
        check(files.translations("ru_ru").values().get("socialrating.saved").equals("Оценка сохранена"), "Client locale must select Russian");
        Path russian = files.directory.resolve("lang/ru_ru.json");
        var ru = JSON.fromJson(Files.readString(russian), JsonObject.class);
        ru.addProperty("socialrating.saved", "Отзыв принят!");
        ru.remove("socialrating.back");
        Files.writeString(russian, JSON.toJson(ru));
        files.reload();
        check(files.translations("ru_ru").values().get("socialrating.saved").equals("Отзыв принят!"), "Editing a bundled language must take effect on reload");
        check(files.translations("ru_ru").values().get("socialrating.back").equals("Назад"), "Missing keys must retain bundled fallback");
        String customBytes = Files.readString(russian);
        new ServerFiles(config, null);
        check(Files.readString(russian).equals(customBytes), "Startup must never overwrite customized language files");
        Files.writeString(files.directory.resolve("lang/fr_fr.json"), "{\"socialrating.save_short\":\"Valider\"}");
        files.reload();
        check(files.translations("fr_fr").values().get("socialrating.save_short").equals("Valider"), "Additional languages must be loaded");
        check(files.translations("fr_fr").values().get("socialrating.category.skill").equals("Skill"), "Additional partial languages must fall back to English");
        String settings = "{\"schemaVersion\":1,\"defaultLanguage\":\"fr_fr\",\"futureOption\":{\"enabled\":true}}";
        Path server = files.directory.resolve("server.json");
        Files.writeString(server, settings);
        files.reload();
        check(files.translations("unavailable").language().equals("fr_fr"), "Unknown locales must use configured default language");
        check(files.translations(null).language().equals("fr_fr"), "Server/console messages must use the configured default");
        new ServerFiles(config, null);
        check(Files.readString(server).equals(settings), "Unknown future settings and formatting must be preserved");
        Files.writeString(russian, "{\"socialrating.saved\":17}");
        expectIo(files::reload);
        check(files.translations("ru_ru").values().get("socialrating.saved").equals("Отзыв принят!"), "Invalid edits must retain previous translation snapshot");
        check(Files.readString(russian).contains("17"), "Invalid edits must not be overwritten");
        Files.writeString(russian, "{broken");
        expectIo(files::reload);
        Files.writeString(russian, customBytes);
        Files.writeString(server, "{\"schemaVersion\":2,\"defaultLanguage\":\"en_us\"}");
        expectIo(files::reload);
        check(files.translations("unavailable").language().equals("fr_fr"), "Bad settings must not partially replace active state");
        Files.writeString(server, settings);
        Files.writeString(files.directory.resolve("lang/xx_xx.json"), "{\"menu.quit\":\"Changed\"}");
        expectIo(files::reload);
        Files.delete(files.directory.resolve("lang/xx_xx.json"));
        files.reload();
        check(files.translations("fr_fr").values().get("socialrating.save_short").equals("Valider"), "Reload must recover after correcting a bad file");

        var author = new Identity(UUID.randomUUID(), "Author");
        var target = new Identity(UUID.randomUUID(), "Target");
        Path legacy = root.resolve("old-world/data/socialrating.json");
        var oldStore = new RatingStore(legacy);
        String review = oldStore.save(author, target, new Integer[] {1, 2, 3}, "Keep my review").get("id").getAsString();
        oldStore.report(target.id(), review, "other", "Keep report too");
        String legacyBytes = Files.readString(legacy);
        Path migratedConfig = root.resolve("migrated-config");
        var migrated = new ServerFiles(migratedConfig, legacy);
        check(migrated.store().profile(author.id(), target.id()).myReview().comment().equals("Keep my review"), "Existing ratings must migrate from the world");
        check(migrated.store().reports().size() == 1, "Migration must preserve reports and snapshots");
        check(Files.readString(legacy).equals(legacyBytes), "Migration must leave the original database untouched");
        Path anotherWorld = root.resolve("another-world/data/socialrating.json");
        new RatingStore(anotherWorld).save(author, target, new Integer[] {3, 3, 3}, "Different world");
        var reopened = new ServerFiles(migratedConfig, anotherWorld);
        check(reopened.store().profile(author.id(), target.id()).myReview().comment().equals("Keep my review"), "Configured database must win when another world is opened");
        Path corrupt = root.resolve("bad-world/data/socialrating.json");
        Files.createDirectories(corrupt.getParent()); Files.writeString(corrupt, "{broken");
        try { new ServerFiles(root.resolve("bad-config"), corrupt); throw new AssertionError("Corrupt migration must fail"); }
        catch (RatingException error) { check(error.code.equals("storage_unavailable"), "Corrupt migration must report storage failure"); }
        check(!Files.exists(root.resolve("bad-config/socialrating/database.json")), "Failed migration must not create an empty replacement database");
        check(Files.readString(corrupt).equals("{broken"), "Corrupt original must be preserved");
    }

    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void expectIo(IoAction action) throws Exception {
        try { action.run(); throw new AssertionError("Expected an invalid configuration error"); }
        catch (IOException expected) { checks++; }
    }
    @FunctionalInterface private interface IoAction { void run() throws Exception; }
}
