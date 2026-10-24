package dev.eyecrasher.socialrating;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import dev.eyecrasher.socialrating.CompatLogger;

/** One host-owned directory for the database, server settings and editable language files. */
public final class ServerFiles implements AutoCloseable {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String RESOURCE = "/assets/socialrating/lang/";
    private static final Map<String, Map<String, String>> BUNDLED = bundled();
    public final Path directory;
    public final Path database;
    private final RatingStore store;
    private volatile Settings settings = new Settings();
    private volatile Map<String, Map<String, String>> languages = BUNDLED;
    private final java.util.concurrent.ScheduledExecutorService worker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
            runnable -> { var thread = new Thread(runnable, "Social Rating storage"); thread.setDaemon(true); return thread; });
    private final java.util.concurrent.atomic.AtomicInteger queued = new java.util.concurrent.atomic.AtomicInteger();

    public static final class Settings extends RatingRules {
        public int schemaVersion = 1;
        public String defaultLanguage = "en_us";
    }

    public ServerFiles(Path configDirectory, Path oldWorldDatabase) throws IOException {
        directory = configDirectory.resolve("socialrating");
        database = directory.resolve("database.json");
        Files.createDirectories(directory.resolve("lang"));
        writeMissing(directory.resolve("server.json"), JSON.toJson(new Settings()) + "\n");
        writeMissing(directory.resolve("server.example.json"), JSON.toJson(new Settings()) + "\n");
        for (var entry : BUNDLED.entrySet())
            writeMissing(directory.resolve("lang/" + entry.getKey() + ".json"), JSON.toJson(entry.getValue()) + "\n");
        if (!Files.exists(database) && oldWorldDatabase != null && Files.isRegularFile(oldWorldDatabase)) {
            new RatingStore(oldWorldDatabase).validate();
            try { Files.copy(oldWorldDatabase, database); }
            catch (FileAlreadyExistsException ignored) {}
        }
        store = new RatingStore(database);
        store.initialize();
        try { reload(); }
        catch (IOException error) { CompatLogger.getLogger("socialrating").warn("Cannot load Social Rating settings/translations; using bundled defaults", error); }
        store.configure(settings);
        if (settings.backupOnStartup) store.backup();
        worker.scheduleWithFixedDelay(() -> {
            try { store.backupIfDue(); }
            catch (RatingException error) { CompatLogger.getLogger("socialrating").error("Cannot back up Social Rating database", error); }
        }, 1, 1, java.util.concurrent.TimeUnit.MINUTES);
    }

    public RatingStore store() { return store; }
    public RatingRules rules() { return settings; }
    public <T> java.util.concurrent.CompletableFuture<T> submit(java.util.function.Supplier<T> task) {
        if (queued.incrementAndGet() > 256) { queued.decrementAndGet(); return dev.eyecrasher.socialrating.JavaCompat.failed(new RatingException("too_many_requests")); }
        try { return java.util.concurrent.CompletableFuture.supplyAsync(() -> { try { return task.get(); } finally { queued.decrementAndGet(); } }, worker); }
        catch (java.util.concurrent.RejectedExecutionException error) { queued.decrementAndGet(); return dev.eyecrasher.socialrating.JavaCompat.failed(new RatingException("unavailable", error)); }
    }
    @Override public void close() {
        worker.shutdown();
        try { if (!worker.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Social Rating storage is still busy"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
    static String defaultText(String key) { return BUNDLED.get("en_us").getOrDefault(key, key); }

    /** Publish a new snapshot only when every file has been validated. */
    public void reload() throws IOException {
        Settings next;
        try (var reader = Files.newBufferedReader(directory.resolve("server.json"))) {
            next = JSON.fromJson(reader, Settings.class);
            if (next == null || next.schemaVersion != 1 || next.defaultLanguage == null)
                throw new IOException("Invalid server.json schema or defaultLanguage");
            next.defaultLanguage = locale(next.defaultLanguage);
            next.validate();
        } catch (RuntimeException error) { throw new IOException("Invalid Social Rating server.json", error); }
        Map<String, Map<String, String>> loaded = new HashMap<>(BUNDLED);
        try (var files = Files.list(directory.resolve("lang"))) {
            for (Path file : files.filter(value -> Files.isRegularFile(value) && value.getFileName().toString().endsWith(".json")).sorted().collect(java.util.stream.Collectors.toList())) {
                String name = file.getFileName().toString();
                String language = locale(name.substring(0, name.length() - 5));
                try (var reader = Files.newBufferedReader(file)) {
                    var object = JSON.fromJson(reader, JsonObject.class);
                    if (object == null) throw new IOException("Empty translation file: " + file);
                    Map<String, String> merged = new LinkedHashMap<>(BUNDLED.getOrDefault(language, dev.eyecrasher.socialrating.JavaCompat.map()));
                    for (var entry : object.entrySet()) {
                        if (!allowedKey(entry.getKey()) || !entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isString())
                            throw new IOException("Invalid translation entry " + entry.getKey() + " in " + file);
                        merged.put(entry.getKey(), entry.getValue().getAsString());
                    }
                    loaded.put(language, dev.eyecrasher.socialrating.JavaCompat.copyMap(merged));
                } catch (RuntimeException error) { throw new IOException("Invalid translation file: " + file, error); }
            }
        }
        if (!loaded.containsKey(next.defaultLanguage)) throw new IOException("No language file for " + next.defaultLanguage);
        settings = next;
        languages = dev.eyecrasher.socialrating.JavaCompat.copyMap(loaded);
        store.configure(next);
    }

    public RatingData.Translations translations(String preferred) {
        String requested;
        try { requested = locale(preferred); } catch (IOException error) { requested = settings.defaultLanguage; }
        String selected = languages.containsKey(requested) ? requested : settings.defaultLanguage;
        Map<String, String> values = new LinkedHashMap<>(languages.get("en_us"));
        values.putAll(languages.get(settings.defaultLanguage));
        values.putAll(languages.get(selected));
        return new RatingData.Translations(selected, dev.eyecrasher.socialrating.JavaCompat.copyMap(values));
    }

    public static boolean allowedKey(String key) {
        return key.startsWith("socialrating.") || key.startsWith("key.socialrating.") || key.startsWith("key.category.socialrating.");
    }

    private static String locale(String value) throws IOException {
        if (value == null) throw new IOException("Missing language code");
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]{1,32}")) throw new IOException("Invalid language code: " + value);
        return normalized;
    }

    private static void writeMissing(Path path, String text) throws IOException {
        try { dev.eyecrasher.socialrating.JavaCompat.write(path, text, java.nio.file.StandardOpenOption.CREATE_NEW); }
        catch (FileAlreadyExistsException ignored) {}
    }

    private static Map<String, Map<String, String>> bundled() {
        Map<String, Map<String, String>> result = new HashMap<>();
        for (String locale : new String[] {"en_us", "ru_ru"}) {
            try (var stream = ServerFiles.class.getResourceAsStream(RESOURCE + locale + ".json")) {
                if (stream == null) throw new IOException("Missing bundled translation: " + locale);
                try (var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                    var json = JSON.fromJson(reader, JsonObject.class);
                    Map<String, String> values = new LinkedHashMap<>();
                    json.entrySet().forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsString()));
                    result.put(locale, dev.eyecrasher.socialrating.JavaCompat.copyMap(values));
                }
            } catch (IOException error) { throw new ExceptionInInitializerError(error); }
        }
        return dev.eyecrasher.socialrating.JavaCompat.copyMap(result);
    }
}
