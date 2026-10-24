package dev.eyecrasher.socialrating;

import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.eyecrasher.socialrating.RatingData.*;

/** Behavioral checks for configurable rules, participant access, withdrawal, backups and large databases. */
public final class RatingFeaturesTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("socialrating-features-");
        try { features(root); largeDatabase(root); System.out.println("PASS: " + checks + " feature, backup and cache checks"); }
        finally { try (var paths = Files.walk(root)) { for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); } }
    }

    private static void features(Path root) throws Exception {
        var author = new Identity(UUID.randomUUID(), "Author"); var target = new Identity(UUID.randomUUID(), "Target");
        var other = UUID.randomUUID(); var store = new RatingStore(root.resolve("database.json")); var rules = new RatingRules(); store.configure(rules);
        String review = store.save(author, target, new Integer[] {5, 4, 5}, "Evidence").get("id").getAsString();
        String report = store.report(target.id(), review, "unfounded", "Complaint").get("id").getAsString();
        store.moderate(report, false, "Moderator", "Reason visible to participants");
        check(store.reportDetails(author.id(), report).decisionReason().equals("Reason visible to participants"), "Author must be able to read the decision reason");
        check(store.reportDetails(target.id(), report).decisionNote().equals("Moderator"), "Target must see operator identity separately from reason");
        check(store.reportPage(other, false, 0).reports().isEmpty(), "An unrelated player must not list other players' reports");
        expect("not_your_report", () -> store.reportDetails(other, report));
        rules.ratingsEnabled = false; expect("ratings_disabled", () -> store.save(author, target, new Integer[] {1, 1, 1}, "")); rules.ratingsEnabled = true;
        rules.allowEditing = false; expect("editing_disabled", () -> store.save(author, target, new Integer[] {1, 1, 1}, "")); rules.allowEditing = true;
        rules.commentsEnabled = false; expect("comments_disabled", () -> store.save(author, target, new Integer[] {1, 1, 1}, "text")); rules.commentsEnabled = true;
        rules.ingenuityEnabled = false; expect("category_disabled", () -> store.save(author, target, new Integer[] {1, 1, 1}, ""));
        rules.allowSkippedCategories = false; expect("categories_required", () -> store.save(author, target, new Integer[] {null, null, 1}, ""));
        store.save(author, target, new Integer[] {null, 4, 5}, "Enabled categories only");
        check(store.profile(author.id(), target.id()).myReview().ingenuity() == null, "Disabled categories may be skipped even when enabled categories are required");
        rules.allowSkippedCategories = true; rules.ingenuityEnabled = true;
        rules.maxCommentLength = 4; expect("invalid_text", () -> store.save(author, target, new Integer[] {1, 1, 1}, "12345")); rules.maxCommentLength = 300;
        rules.reportsEnabled = false; expect("reports_disabled", () -> store.report(target.id(), review, "abuse", "")); rules.reportsEnabled = true;
        rules.maxReportDetailsLength = 4; expect("invalid_text", () -> store.report(target.id(), review, "abuse", "12345")); rules.maxReportDetailsLength = 300;
        String next = store.report(target.id(), review, "abuse", "Second revision").get("id").getAsString();
        expect("invalid_text", () -> store.resolve(next, true, "x".repeat(301)));
        check(store.reportDetails(next).status().equals("pending") && store.profile(target.id(), target.id()).summary().count() == 1,
                "An error during a transaction must not leak partially removed reviews into the cache");
        rules.maxDecisionReasonLength = 4; expect("invalid_text", () -> store.moderate(next, true, "Moderator", "12345")); rules.maxDecisionReasonLength = 200;
        check(store.reportDetails(next).status().equals("pending"), "Invalid reasons must not partly resolve the report");
        rules.allowWithdrawal = false; expect("withdrawal_disabled", () -> store.withdraw(author.id(), review)); rules.allowWithdrawal = true;
        expect("not_review_author", () -> store.withdraw(target.id(), review));
        store.withdraw(author.id(), review);
        check(store.profile(target.id(), target.id()).summary().count() == 0 && store.profile(author.id(), author.id()).sentCount() == 0,
                "Withdrawn reviews must disappear from sent/received lists and aggregates");
        check(store.reportDetails(next).status().equals("withdrawn") && store.reportPage(true, 0).pending() == 0, "Withdrawal must close pending complaints without an admin punishment");
        expect("review_not_found", () -> store.withdraw(author.id(), review));
        expect("report_resolved", () -> store.moderate(next, true, "Moderator", ""));
        String replacement = store.save(author, target, new Integer[] {5, 5, 5}, "New review").get("id").getAsString();
        check(!replacement.equals(review) && new RatingStore(root.resolve("database.json")).profile(author.id(), target.id()).summary().count() == 1,
                "Withdrawal must allow a new review and survive a store reopen");
        rules.backupRetention = 2;
        Path backups = root.resolve("backups");
        store.backup(); store.backup(); store.backup();
        List<Path> snapshots;
        try (var paths = Files.list(backups)) { snapshots = paths.filter(p -> p.toString().endsWith(".json")).toList(); }
        check(snapshots.size() == 2, "Automatic retention must preserve only the configured number of backups");
        for (var snapshot : snapshots) check(new RatingStore(snapshot).profile(author.id(), target.id()).myReview().id().equals(replacement), "Backup must reopen with complete reviews and history");
        Files.writeString(backups.resolve("owner-notes.json"), "Keep me"); store.backup();
        check(Files.readString(backups.resolve("owner-notes.json")).equals("Keep me"), "Retention must not delete unrelated files");
        rules.backupsEnabled = false; store.backup();
        try (var paths = Files.list(backups)) { check(paths.count() == 3, "Disabled backups must not create extra files"); }
        rules.backupsEnabled = true;
        String good = Files.readString(root.resolve("database.json")); Files.writeString(root.resolve("database.json"), "{broken");
        expect("storage_unavailable", store::backup); Files.writeString(root.resolve("database.json"), good);
        check(new RatingStore(root.resolve("database.json")).reportDetails(report).decisionReason().equals("Reason visible to participants"), "Decision reasons must survive restart and backups");
        store.profile(author.id(), target.id());
        Files.delete(root.resolve("database.json"));
        expect("storage_unavailable", () -> store.save(author, target, new Integer[] {1, 1, 1}, "Do not reset a missing database"));
        Files.writeString(root.resolve("database.json"), good);

        Path config = root.resolve("config");
        try (var files = new ServerFiles(config, null)) {
            Files.writeString(files.directory.resolve("server.json"), "{\"ratingDistance\":12,\"commentsEnabled\":false,\"maxCommentLength\":42,\"backupRetention\":3}");
            files.reload();
            check(files.rules().ratingDistance == 12 && !files.rules().commentsEnabled && files.rules().maxCommentLength == 42, "Rules must reload while omitted keys retain defaults");
            check(files.submit(() -> Thread.currentThread().getName()).join().equals("Social Rating storage"), "Storage work must run on its serial worker");
            var gate = new java.util.concurrent.CountDownLatch(1);
            var blocked = files.submit(() -> { try { gate.await(); } catch (InterruptedException error) { throw new RuntimeException(error); } return true; });
            var queued = new ArrayList<java.util.concurrent.CompletableFuture<Boolean>>();
            try {
                for (int i = 0; i < 257; i++) queued.add(files.submit(() -> true));
                check(queued.stream().anyMatch(java.util.concurrent.CompletableFuture::isCompletedExceptionally), "Storage backlog must reject overload instead of growing indefinitely");
            } finally { gate.countDown(); }
            check(blocked.join(), "Accepted work must still finish after overload");
            for (var task : queued) task.handle((value, error) -> true).join();
            Files.writeString(files.directory.resolve("server.json"), "{\"ratingDistance\":0,\"commentsEnabled\":true}");
            try { files.reload(); throw new AssertionError("Invalid settings accepted"); } catch (java.io.IOException expected) { checks++; }
            check(files.rules().ratingDistance == 12 && !files.rules().commentsEnabled, "Invalid reload must keep the previous rules atomically");
        }
    }

    private static void largeDatabase(Path root) throws Exception {
        int count = 10_000;
        var target = new Identity(UUID.randomUUID(), "Target"); var reviews = new ArrayList<Review>();
        for (int i = 0; i < count; i++) reviews.add(new Review(new UUID(2, i).toString(), new UUID(1, i).toString(), target.id().toString(),
                "Author" + i, target.name(), 5, 4, 3, i >= count - 100 ? "&".repeat(1000) : "Large database review " + i, "2026-10-24T10:00:00Z", 1, ""));
        Path file = root.resolve("large.json");
        Files.writeString(file, new Gson().toJson(Map.of("schema", 2, "names", Map.of(), "reviews", reviews, "reports", List.of(), "removed", List.of())));
        var store = new RatingStore(file); var rules = new RatingRules(); rules.profileReviewLimit = 7; store.configure(rules);
        check(store.profile(target.id(), target.id()).reviewCount() == count && store.profile(target.id(), target.id()).reviews().size() == 7,
                "Profile limits must not truncate total counts on a large database");
        rules.profileReviewLimit = 100;
        var bounded = store.profile(target.id(), target.id());
        check(new Gson().toJson(bounded).length() < 262144 && bounded.reviews().size() < 100 && bounded.reviewCount() == count,
                "Long escaped comments must fit the wire budget without losing aggregate counts or truncating text");
        rules.profileReviewLimit = 7;
        long reads = store.diskReads(), started = System.nanoTime();
        var targets = java.util.stream.IntStream.range(0, 64).mapToObj(i -> i == 0 ? target.id() : new UUID(3, i)).toList();
        for (int i = 0; i < 1000; i++) check(store.summaries(targets).get(target.id().toString()).average() == 4, "Cached summaries must remain accurate");
        check(store.diskReads() == reads && reads == 1, "Repeated queries must not reparse the file");
        System.out.println("10,000 reviews / 1,000 queries of 64 players: " + (System.nanoTime() - started) / 1_000_000 + " ms; disk parses=" + store.diskReads());
        new RatingStore(file).save(new Identity(UUID.randomUUID(), "External"), target, new Integer[] {5, 5, 5}, "Other store");
        check(store.profile(target.id(), target.id()).summary().count() == count + 1 && store.diskReads() == reads + 1,
                "Atomic file replacement from another process/store must invalidate caches");
    }

    private static void check(boolean result, String message) { checks++; if (!result) throw new AssertionError(message); }
    private static void expect(String code, Runnable action) {
        try { action.run(); } catch (RatingException error) { check(error.code.equals(code), "Expected " + code + ", got " + error.code); return; }
        throw new AssertionError("Expected " + code);
    }
}
