package dev.eyecrasher.socialrating;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static dev.eyecrasher.socialrating.RatingData.*;

/** Persistent server-owned reviews. */
public final class RatingStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();
    private final Path path;
    private volatile RatingRules rules = new RatingRules();
    private Database cached;
    private java.nio.file.attribute.BasicFileAttributes stamp;
    private long diskReads, lastBackup = System.currentTimeMillis();

    public RatingStore(Path path) { this.path = path.toAbsolutePath().normalize(); }
    public void configure(RatingRules rules) { this.rules = rules; }
    long diskReads() { return diskReads; }

    public void validate() { access(false, data -> null); }
    public void initialize() { access(true, data -> null); }

    private static final class Database {
        int schema = 2;
        transient boolean migrated;
        Map<String, String> names = new HashMap<>();
        List<Review> reviews = new ArrayList<>();
        List<Report> reports = new ArrayList<>();
        Set<String> removed = new HashSet<>();
        transient Map<String, Review> activePairs, byId;
        transient Map<String, List<Review>> received, sent;
        transient Map<String, Summary> summaries;
        transient Map<String, String> reportStatuses;
    }

    public static final class Report  {
        private final String id;
        private final String reviewId;
        private final int revision;
        private final String reporter;
        private final String reason;
        private final String details;
        private final Review snapshot;
        private final String createdAt;
        private final String status;
        private final String decisionNote;
        private final String decisionReason;
        public Report(String id, String reviewId, int revision, String reporter, String reason, String details, Review snapshot, String createdAt, String status, String decisionNote, String decisionReason) {
            this.id = id;
            this.reviewId = reviewId;
            this.revision = revision;
            this.reporter = reporter;
            this.reason = reason;
            this.details = details;
            this.snapshot = snapshot;
            this.createdAt = createdAt;
            this.status = status;
            this.decisionNote = decisionNote;
            this.decisionReason = decisionReason;
        }
        public String id() { return id; }
        public String reviewId() { return reviewId; }
        public int revision() { return revision; }
        public String reporter() { return reporter; }
        public String reason() { return reason; }
        public String details() { return details; }
        public Review snapshot() { return snapshot; }
        public String createdAt() { return createdAt; }
        public String status() { return status; }
        public String decisionNote() { return decisionNote; }
        public String decisionReason() { return decisionReason; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            Report that = (Report) other;
            return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(reviewId, that.reviewId) && java.util.Objects.equals(revision, that.revision) && java.util.Objects.equals(reporter, that.reporter) && java.util.Objects.equals(reason, that.reason) && java.util.Objects.equals(details, that.details) && java.util.Objects.equals(snapshot, that.snapshot) && java.util.Objects.equals(createdAt, that.createdAt) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(decisionNote, that.decisionNote) && java.util.Objects.equals(decisionReason, that.decisionReason);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, reviewId, revision, reporter, reason, details, snapshot, createdAt, status, decisionNote, decisionReason); }
}

    public Profile profile(UUID viewer, UUID target) {
        return access(false, data -> {
            String id = target.toString();
            var received = reviews(data, false, id);
            var sent = viewer.equals(target) ? reviews(data, true, id) : dev.eyecrasher.socialrating.JavaCompat.<Review>list();
            Review mine = received.stream().filter(review -> review.author().equals(viewer.toString())).findFirst().orElse(null);
            return new Profile(id, data.names.getOrDefault(id, ""), summary(data, id),
                    bounded(received, rules.profileReviewLimit), bounded(sent, rules.profileReviewLimit), mine,
                    received.size(), sent.size());
        });
    }

    private static List<Review> bounded(List<Review> reviews, int limit) {
        var result = new ArrayList<Review>();
        int size = 0;
        for (var review : reviews.stream().limit(limit).collect(java.util.stream.Collectors.toList())) {
            size += JSON.toJson(review).length();
            if (size > 110_000) break;
            result.add(review);
        }
        return dev.eyecrasher.socialrating.JavaCompat.copyList(result);
    }

    public Map<String, Summary> summaries(List<UUID> targets) {
        return access(false, data -> {
            Map<String, Summary> result = new LinkedHashMap<>();
            for (var target : targets) result.put(target.toString(), summary(data, target.toString()));
            return result;
        });
    }

    public JsonObject save(Identity author, Identity target, Integer[] scores, String comment) {
        if (!rules.ratingsEnabled) throw new RatingException("ratings_disabled");
        if (author.id().equals(target.id())) throw new RatingException("self_rating");
        if (scores.length != 3) throw new RatingException("invalid_score");
        boolean any = false;
        for (int i = 0; i < scores.length; i++) {
            Integer score = scores[i];
            if (!rules.categoryEnabled(i) && score != null) throw new RatingException("category_disabled");
            if (rules.categoryEnabled(i) && !rules.allowSkippedCategories && score == null) throw new RatingException("categories_required");
            if (score != null && (score < 1 || score > MAX_SCORE)) throw new RatingException("invalid_score");
            any |= score != null;
        }
        if (!any) throw new RatingException("empty_rating");
        if (!rules.commentsEnabled && !dev.eyecrasher.socialrating.JavaCompat.blank(comment)) throw new RatingException("comments_disabled");
        String body = clean(comment, rules.maxCommentLength, false), authorName = clean(author.name(), 32, true), targetName = clean(target.name(), 32, true);
        return access(true, data -> {
            index(data);
            Review existing = data.activePairs.get(author.id() + ":" + target.id());
            if (existing != null && !rules.allowEditing) throw new RatingException("editing_disabled");
            Review review = new Review(existing == null ? UUID.randomUUID().toString() : existing.id(), author.id().toString(),
                    target.id().toString(), authorName, targetName, scores[0], scores[1], scores[2], body,
                    Instant.now().toString(), existing == null ? 1 : existing.revision() + 1, "");
            data.names.put(author.id().toString(), authorName);
            data.names.put(target.id().toString(), targetName);
            if (existing != null) data.reviews.remove(existing);
            data.reviews.add(review);
            return id(review.id());
        });
    }

    public JsonObject report(UUID reporter, String reviewId, String reason, String details) {
        if (!rules.reportsEnabled) throw new RatingException("reports_disabled");
        if (reason == null || !dev.eyecrasher.socialrating.JavaCompat.set("unfounded", "abuse", "spam", "other").contains(reason)) throw new RatingException("invalid_reason");
        String body = clean(details, rules.maxReportDetailsLength, false);
        return access(true, data -> {
            Review review = data.reviews.stream().filter(value -> value.id().equals(reviewId) && !data.removed.contains(value.id()))
                    .findFirst().orElseThrow(() -> new RatingException("review_not_found"));
            if (!review.target().equals(reporter.toString())) throw new RatingException("not_your_review");
            Report report = data.reports.stream().filter(value -> value.reviewId().equals(reviewId)
                    && value.revision() == review.revision() && value.reporter().equals(reporter.toString())).findFirst().orElse(null);
            boolean created = report == null;
            if (created) {
                report = new Report(UUID.randomUUID().toString(), reviewId, review.revision(), reporter.toString(), reason,
                        body, review, Instant.now().toString(), "pending", "", "");
                data.reports.add(report);
            }
            JsonObject result = id(report.id());
            result.addProperty("status", report.status());
            result.addProperty("created", created);
            return result;
        });
    }

    public List<Report> reports() { return access(false, data -> dev.eyecrasher.socialrating.JavaCompat.copyList(data.reports)); }

    public ReportPage reportPage(boolean pending, int page) {
        return reportPage(null, pending, page);
    }

    public ReportPage reportPage(UUID participant, boolean pending, int page) {
        return access(false, data -> {
            var visible = data.reports.stream().filter(report -> participant == null || involved(report, participant)).collect(java.util.stream.Collectors.toList());
            int waiting = (int) visible.stream().filter(report -> report.status().equals("pending")).count();
            var filtered = visible.stream().filter(report -> report.status().equals("pending") == pending)
                    .sorted(Comparator.comparing(Report::createdAt).thenComparing(Report::id).reversed()).collect(java.util.stream.Collectors.toList());
            int pages = Math.max(1, (filtered.size() + 1) / 2);
            int selected = dev.eyecrasher.socialrating.JavaCompat.clamp(page, 0, pages - 1);
            return new ReportPage(filtered.stream().skip(selected * 2L).limit(2).map(report -> moderation(data, report)).collect(java.util.stream.Collectors.toList()),
                    selected, pages, waiting, visible.size() - waiting);
        });
    }

    public ModerationReport reportDetails(String id) {
        return reportDetails(null, id);
    }

    public ModerationReport reportDetails(UUID participant, String id) {
        return access(false, data -> {
            Report report = findReport(data, id);
            if (participant != null && !involved(report, participant)) throw new RatingException("not_your_report");
            return moderation(data, report);
        });
    }

    private static boolean involved(Report report, UUID participant) {
        return report.reporter().equals(participant.toString()) || report.snapshot().author().equals(participant.toString());
    }

    public JsonObject withdraw(UUID author, String reviewId) {
        if (!rules.allowWithdrawal) throw new RatingException("withdrawal_disabled");
        return access(true, data -> {
            index(data);
            Review review = data.byId.get(reviewId);
            if (review == null || data.removed.contains(reviewId)) throw new RatingException("review_not_found");
            if (!review.author().equals(author.toString())) throw new RatingException("not_review_author");
            data.removed.add(reviewId);
            for (int i = 0; i < data.reports.size(); i++) {
                Report report = data.reports.get(i);
                if (report.reviewId().equals(reviewId) && report.status().equals("pending"))
                    data.reports.set(i, new Report(report.id(), report.reviewId(), report.revision(), report.reporter(), report.reason(),
                            report.details(), report.snapshot(), report.createdAt(), "withdrawn", review.authorName(), ""));
            }
            return id(reviewId);
        });
    }

    public ModerationReport moderate(String id, boolean remove, String operator, String note) {
        String cleanedNote = clean(note, rules.maxDecisionReasonLength, false);
        String decision = clean(operator, 32, true);
        return access(true, data -> {
            Report report = findReport(data, id);
            if (!report.status().equals("pending")) throw new RatingException("report_resolved");
            if (remove && moderation(data, report).outdated()) throw new RatingException("review_changed");
            resolve(data, report, remove, decision, cleanedNote);
            return moderation(data, findReport(data, id));
        });
    }

    private static Report findReport(Database data, String id) {
        return data.reports.stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new RatingException("report_not_found"));
    }

    private static ModerationReport moderation(Database data, Report report) {
        boolean outdated = data.removed.contains(report.reviewId()) || data.reviews.stream().noneMatch(
                review -> review.id().equals(report.reviewId()) && review.revision() == report.revision());
        return new ModerationReport(report.id(), data.names.getOrDefault(report.reporter(), report.snapshot().targetName()),
                report.reason(), report.details(), report.snapshot(), report.createdAt(), report.status(), report.decisionNote(), outdated,
                report.decisionReason() == null ? "" : report.decisionReason());
    }

    public boolean resolve(String id, boolean remove, String note) {
        return access(true, data -> resolve(data, findReport(data, id), remove, note, ""));
    }

    private static boolean resolve(Database data, Report report, boolean remove, String note, String reason) {
        if (!report.status().equals("pending")) throw new RatingException("report_resolved");
        boolean removed = remove && data.reviews.stream().anyMatch(review -> review.id().equals(report.reviewId())
                && review.revision() == report.revision()) && data.removed.add(report.reviewId());
        data.reports.set(data.reports.indexOf(report), new Report(report.id(), report.reviewId(), report.revision(),
                report.reporter(), report.reason(), report.details(), report.snapshot(), report.createdAt(), removed ? "removed" : "kept", clean(note, 300, false), reason));
        return removed;
    }

    private static List<Review> reviews(Database data, boolean sent, String target) {
        index(data);
        return (sent ? data.sent : data.received).getOrDefault(target, dev.eyecrasher.socialrating.JavaCompat.list()).stream()
                .map(review -> {
                    String status = data.reportStatuses.getOrDefault(review.id() + ":" + review.revision(), "");
                    return new Review(review.id(), review.author(), review.target(), data.names.getOrDefault(review.author(), review.authorName()),
                            data.names.getOrDefault(review.target(), review.targetName()), review.ingenuity(), review.skill(),
                            review.friendliness(), review.comment(), review.updatedAt(), review.revision(), status);
                }).collect(java.util.stream.Collectors.toList());
    }

    private static Summary summary(Database data, String target) {
        index(data);
        return data.summaries.computeIfAbsent(target, id -> calculateSummary(data, id));
    }

    private static Summary calculateSummary(Database data, String target) {
        int count = 0;
        int[] sums = new int[3], votes = new int[3];
        for (Review review : data.received.getOrDefault(target, dev.eyecrasher.socialrating.JavaCompat.list())) {
            count++;
            Integer[] scores = {review.ingenuity(), review.skill(), review.friendliness()};
            for (int i = 0; i < 3; i++) if (scores[i] != null) { sums[i] += scores[i]; votes[i]++; }
        }
        return new Summary(target, count, mean(sums[0] + sums[1] + sums[2], votes[0] + votes[1] + votes[2]),
                mean(sums[0], votes[0]), mean(sums[1], votes[1]), mean(sums[2], votes[2]), votes[0], votes[1], votes[2]);
    }

    private static void index(Database data) {
        if (data.byId != null) return;
        data.byId = new HashMap<>(); data.activePairs = new HashMap<>(); data.received = new HashMap<>(); data.sent = new HashMap<>();
        data.summaries = new HashMap<>(); data.reportStatuses = new HashMap<>();
        for (Review review : data.reviews) {
            data.byId.put(review.id(), review);
            if (data.removed.contains(review.id())) continue;
            data.activePairs.put(review.author() + ":" + review.target(), review);
            data.received.computeIfAbsent(review.target(), unused -> new ArrayList<>()).add(review);
            data.sent.computeIfAbsent(review.author(), unused -> new ArrayList<>()).add(review);
        }
        var order = Comparator.comparing(Review::updatedAt).thenComparing(Review::id).reversed();
        data.received.values().forEach(list -> list.sort(order)); data.sent.values().forEach(list -> list.sort(order));
        for (Report report : data.reports) data.reportStatuses.put(report.reviewId() + ":" + report.revision(), report.status());
    }

    private static Database copy(Database source) {
        var next = new Database(); next.schema = source.schema; next.migrated = source.migrated;
        next.names = new HashMap<>(source.names); next.reviews = new ArrayList<>(source.reviews);
        next.reports = new ArrayList<>(source.reports); next.removed = new HashSet<>(source.removed);
        return next;
    }

    private static Double mean(int sum, int votes) { return votes == 0 ? null : (double) sum / votes; }
    private static JsonObject id(String value) { var result = new JsonObject(); result.addProperty("id", value); return result; }

    private static String clean(String value, int max, boolean required) {
        if (value == null || value.codePointCount(0, value.length()) > max) throw new RatingException("invalid_text");
        var result = new StringBuilder();
        value.codePoints().filter(c -> c != '§' && !Character.isISOControl(c) && Character.getType(c) != Character.FORMAT)
                .forEach(result::appendCodePoint);
        String cleaned = dev.eyecrasher.socialrating.JavaCompat.strip(result.toString()).replaceAll("\\s+", " ");
        if (required && cleaned.isEmpty()) throw new RatingException("invalid_text");
        return cleaned;
    }

    private <T> T access(boolean write, Function<Database, T> action) {
        synchronized (LOCKS.computeIfAbsent(path, unused -> new Object())) {
            try {
                Files.createDirectories(path.getParent());
                if (!write) return action.apply(load());
                try (var channel = FileChannel.open(path.resolveSibling(path.getFileName() + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                     var ignored = channel.lock()) {
                    Database data = copy(load());
                    T result = action.apply(data);
                    if (write) {
                        if (data.migrated) {
                            Path backup = path.resolveSibling(path.getFileName() + ".v1.bak");
                            if (!Files.exists(backup)) Files.copy(path, backup);
                        }
                        persist(data);
                        data.byId = null; // Rebuild derived indexes after a successful mutation.
                        cached = data; stamp = attributes();
                    }
                    return result;
                }
            } catch (IOException | com.google.gson.JsonParseException error) {
                throw new RatingException("storage_unavailable", error);
            }
        }
    }

    private java.nio.file.attribute.BasicFileAttributes attributes() throws IOException {
        return Files.exists(path) ? Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class) : null;
    }

    private Database load() throws IOException {
        var current = attributes();
        if (current == null && stamp != null) throw new IOException("Social Rating database disappeared; restore it before continuing");
        if (cached == null || (current == null) != (stamp == null) || (current != null
                && (current.size() != stamp.size() || !current.lastModifiedTime().equals(stamp.lastModifiedTime())
                || !java.util.Objects.equals(current.fileKey(), stamp.fileKey())))) {
            cached = read(); stamp = current;
        }
        return cached;
    }

    public void backupIfDue() {
        if (rules.backupsEnabled && System.currentTimeMillis() - lastBackup >= rules.backupIntervalMinutes * 60_000L) backup();
    }

    public void backup() {
        if (!rules.backupsEnabled) return;
        synchronized (LOCKS.computeIfAbsent(path, unused -> new Object())) {
            try {
                load(); // Never back up corrupt or unsupported input as a healthy snapshot.
                if (!Files.exists(path)) return;
                Path directory = path.getParent().resolve("backups"); Files.createDirectories(directory);
                String name = "database-" + java.time.format.DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS")
                        .withZone(java.time.ZoneOffset.UTC).format(Instant.now()) + "-" + UUID.randomUUID() + ".json";
                Path temporary = Files.createTempFile(directory, ".backup-", ".tmp");
                try {
                    Files.copy(path, temporary, StandardCopyOption.REPLACE_EXISTING);
                    new RatingStore(temporary).validate();
                    try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
                    try { Files.move(temporary, directory.resolve(name), StandardCopyOption.ATOMIC_MOVE); }
                    catch (AtomicMoveNotSupportedException error) { Files.move(temporary, directory.resolve(name)); }
                } finally {
                    Files.deleteIfExists(temporary); Files.deleteIfExists(temporary.resolveSibling(temporary.getFileName() + ".lock"));
                    LOCKS.remove(temporary);
                }
                try (var files = Files.list(directory)) {
                    var backups = files.filter(file -> file.getFileName().toString().matches("database-[0-9]{8}-[0-9]{6}-[0-9]{3}-[a-f0-9-]+\\.json"))
                            .sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList());
                    for (var old : backups.stream().skip(rules.backupRetention).collect(java.util.stream.Collectors.toList())) Files.delete(old);
                }
                lastBackup = System.currentTimeMillis();
            } catch (IOException | com.google.gson.JsonParseException error) { throw new RatingException("storage_unavailable", error); }
        }
    }

    private Database read() throws IOException {
        if (!Files.exists(path)) return new Database();
        diskReads++;
        try (var reader = Files.newBufferedReader(path)) {
            JsonObject raw = JSON.fromJson(reader, JsonObject.class);
            if (raw == null || !raw.has("schema") || (raw.get("schema").getAsInt() != 1 && raw.get("schema").getAsInt() != 2)
                    || !raw.has("reviews") || !raw.has("reports") || !raw.has("names") || !raw.has("removed"))
                throw new IOException("Unsupported Social Rating database");
            Database data = JSON.fromJson(raw, Database.class);
            if (data.reviews == null || data.reports == null || data.names == null || data.removed == null)
                throw new IOException("Incomplete Social Rating database");
            for (Review review : data.reviews) validateReview(review, data.schema);
            for (Report report : data.reports) {
                if (report == null || report.id() == null || report.reviewId() == null || report.reporter() == null || report.status() == null
                        || report.snapshot() == null || report.createdAt() == null || report.reason() == null || report.details() == null || report.decisionNote() == null)
                    throw new IOException("Incomplete Social Rating report");
                validateReview(report.snapshot(), data.schema);
                Instant.parse(report.createdAt());
            }
            if (data.schema == 1) {
                data.reviews = data.reviews.stream().map(RatingStore::upgrade).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                data.reports = data.reports.stream().map(report -> new Report(report.id(), report.reviewId(), report.revision(), report.reporter(),
                        report.reason(), report.details(), upgrade(report.snapshot()), report.createdAt(), report.status(), report.decisionNote(), report.decisionReason()))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
                data.schema = 2;
                data.migrated = true;
            }
            return data;
        } catch (IllegalStateException | IllegalArgumentException | java.time.format.DateTimeParseException error) {
            throw new IOException("Invalid Social Rating database", error);
        }
    }

    private static void validateReview(Review review, int schema) throws IOException {
        if (review == null || review.id() == null || review.author() == null || review.target() == null
                || review.comment() == null || review.updatedAt() == null || review.authorName() == null || review.targetName() == null)
            throw new IOException("Incomplete Social Rating review");
        UUID.fromString(review.id()); UUID.fromString(review.author()); UUID.fromString(review.target());
        try { Instant.parse(review.updatedAt()); }
        catch (java.time.format.DateTimeParseException error) { throw new IOException("Invalid Social Rating review date", error); }
        for (Integer score : new Integer[] {review.ingenuity(), review.skill(), review.friendliness()})
            if (score != null && (score < 1 || score > (schema == 1 ? 3 : MAX_SCORE))) throw new IOException("Invalid Social Rating score");
    }

    private static Review upgrade(Review review) {
        return new Review(review.id(), review.author(), review.target(), review.authorName(), review.targetName(),
                upgrade(review.ingenuity()), upgrade(review.skill()), upgrade(review.friendliness()), review.comment(),
                review.updatedAt(), review.revision(), review.reportStatus());
    }

    private static Integer upgrade(Integer score) { return score == null ? null : score * 2 - 1; }

    private void persist(Database data) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), "ratings-", ".tmp");
        try {
            dev.eyecrasher.socialrating.JavaCompat.write(temporary, JSON.toJson(data) + "\n");
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
