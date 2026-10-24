package dev.eyecrasher.socialrating;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import static dev.eyecrasher.socialrating.RatingData.*;

/** Persistence and concurrency checks run by Gradle check, without Minecraft or a network service. */
public final class RatingStoreTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length == 4 && args[0].equals("worker")) {
            var store = new RatingStore(Path.of(args[1]));
            var author = new Identity(UUID.fromString(args[2]), "OtherClient");
            var target = new Identity(UUID.fromString(args[3]), "Sam");
            for (int i = 0; i < 8; i++) store.save(author, target, new Integer[] {3, 2, 1}, "From another JVM " + i);
            return;
        }
        Path root = Files.createTempDirectory("socialrating-store-test-");
        try {
            run(root);
            System.out.println("PASS: " + checks + " server storage checks");
        } finally {
            try (var files = Files.walk(root)) {
                for (var path : files.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static void run(Path root) throws Exception {
        Path file = root.resolve("ratings.json");
        var store = new RatingStore(file);
        var alex = new Identity(UUID.randomUUID(), "Alex");
        var sam = new Identity(UUID.randomUUID(), "Sam");
        var mia = new Identity(UUID.randomUUID(), "Mia");
        var empty = store.profile(alex.id(), sam.id());
        check(empty.summary().count() == 0 && empty.summary().average() == null, "A new host world should have an empty profile");
        var original = store.save(alex, sam, new Integer[] {3, null, 1}, "Привет, друг!");
        var restarted = new RatingStore(file);
        var result = restarted.profile(alex.id(), sam.id());
        check(result.myReview().comment().equals("Привет, друг!"), "Unicode review should survive a new store instance");
        check(result.summary().average() == 2 && result.summary().skill() == null && result.summary().skillCount() == 0,
                "A skipped category must not contribute zero to the average");
        var edited = restarted.save(alex, sam, new Integer[] {2, 3, 3}, "Изменил мнение");
        check(edited.get("id").equals(original.get("id")), "Editing must retain the review ID");
        check(store.profile(alex.id(), sam.id()).summary().count() == 1, "An edit must not create another vote");
        check(store.profile(alex.id(), sam.id()).myReview().revision() == 2, "An edit must increment the revision");
        store.save(mia, sam, new Integer[] {null, 1, null}, "");
        var summary = store.summaries(List.of(sam.id())).get(sam.id().toString());
        check(summary.count() == 2 && summary.skillCount() == 2 && summary.skill() == 2 && summary.average() == 2.25,
                "Different authors and missing categories must aggregate correctly");
        check(store.profile(alex.id(), alex.id()).sentCount() == 1, "The author's own sent list must contain the review");
        check(store.profile(mia.id(), alex.id()).sent().isEmpty(), "Another viewer must not receive a private sent list");
        expect("self_rating", () -> store.save(alex, alex, new Integer[] {1, 1, 1}, ""));
        expect("empty_rating", () -> store.save(alex, sam, new Integer[3], ""));
        expect("invalid_score", () -> store.save(alex, sam, new Integer[] {6, 1, 1}, ""));
        expect("invalid_score", () -> store.save(alex, sam, new Integer[] {0, 1, 1}, ""));
        expect("invalid_text", () -> store.save(alex, sam, new Integer[] {1, 1, 1}, "я".repeat(301)));
        String reviewId = original.get("id").getAsString();
        expect("not_your_review", () -> store.report(mia.id(), reviewId, "abuse", ""));
        var report = store.report(sam.id(), reviewId, "unfounded", "Не согласен");
        var duplicate = store.report(sam.id(), reviewId, "spam", "Again");
        check(report.get("id").equals(duplicate.get("id")) && !duplicate.get("created").getAsBoolean(), "Repeating a report must return the same report without a second notification");
        check(store.profile(sam.id(), sam.id()).reviews().stream().anyMatch(r -> r.reportStatus().equals("pending")),
                "A report must show pending status without hiding the review");
        String evidence = Files.readString(file);
        store.save(alex, sam, new Integer[] {3, 3, 3}, "New revision");
        check(store.profile(alex.id(), sam.id()).myReview().reportStatus().isEmpty(), "An old report must not apply to a new revision");
        check(Files.readString(file).contains("Изменил мнение"), "The report must preserve the old review snapshot");
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = java.util.stream.IntStream.range(0, 16).mapToObj(i -> CompletableFuture.runAsync(() ->
                    new RatingStore(file).save(alex, sam, new Integer[] {3, 2, 1}, "Concurrent edit " + i), pool)).toList();
            CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).join();
        }
        check(store.profile(alex.id(), sam.id()).summary().count() == 2, "Concurrent stores must not duplicate or lose votes");
        check(store.profile(alex.id(), sam.id()).myReview().revision() == 19, "Concurrent edits must not overwrite each other's revisions");
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var first = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"), RatingStoreTest.class.getName(),
                "worker", file.toString(), alex.id().toString(), sam.id().toString()).inheritIO().start();
        var second = new ProcessBuilder(javaExecutable, "-cp", System.getProperty("java.class.path"), RatingStoreTest.class.getName(),
                "worker", file.toString(), mia.id().toString(), sam.id().toString()).inheritIO().start();
        try {
            check(first.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) && first.exitValue() == 0, "First separate JVM must be able to save");
            check(second.waitFor(15, java.util.concurrent.TimeUnit.SECONDS) && second.exitValue() == 0, "Second separate JVM must be able to save");
        } finally { first.destroyForcibly(); second.destroyForcibly(); }
        var shared = store.profile(alex.id(), sam.id());
        check(shared.summary().count() == 2 && shared.myReview().revision() == 27,
                "Separate JVMs must share reviews without lost updates or extra votes");
        check(!store.resolve(report.get("id").getAsString(), true, "Old revision"), "A decision on an older report must not remove an edited review");
        var latest = store.report(sam.id(), reviewId, "abuse", "Current revision");
        check(store.resolve(latest.get("id").getAsString(), true, "Reviewed"), "The current review should be removed on a removal decision");
        check(store.profile(sam.id(), sam.id()).summary().count() == 1, "Removed review must be excluded from the aggregate");
        check(store.profile(alex.id(), sam.id()).myReview() == null && store.profile(alex.id(), alex.id()).sentCount() == 0,
                "A removed review must not occupy the author's active review slot");
        String replacement = new RatingStore(file).save(alex, sam, new Integer[] {1, 2, 3}, "New review").get("id").getAsString();
        check(!replacement.equals(reviewId), "A new review after removal must receive its own ID");
        var replacementProfile = new RatingStore(file).profile(alex.id(), sam.id());
        check(replacementProfile.summary().count() == 2 && replacementProfile.myReview().revision() == 1
                        && replacementProfile.myReview().reportStatus().isEmpty(),
                "Only the new review must count, without inheriting the old complaint");
        check(store.reportDetails(latest.get("id").getAsString()).status().equals("removed")
                        && store.reportDetails(latest.get("id").getAsString()).snapshot().id().equals(reviewId),
                "Posting again must preserve the removed review's moderation history");
        check(store.save(alex, sam, new Integer[] {5, 4, 5}, "Edit new review").get("id").getAsString().equals(replacement)
                        && store.profile(alex.id(), alex.id()).sentCount() == 1 && store.profile(alex.id(), sam.id()).myReview().revision() == 2,
                "Further edits must update the replacement, retaining only one active review per author and target");
        expect("report_resolved", () -> store.resolve(latest.get("id").getAsString(), false, "Again"));
        Files.writeString(file, "{broken");
        expect("storage_unavailable", () -> store.save(alex, sam, new Integer[] {1, 2, 3}, "Do not replace corrupt data"));
        check(Files.readString(file).equals("{broken"), "Corrupt input must be preserved rather than reset");
        Files.writeString(file, evidence.replace("\"schema\": 2", "\"schema\": 999"));
        expect("storage_unavailable", () -> store.profile(alex.id(), sam.id()));
        check(Files.readString(file).contains("999"), "Unsupported schema must be preserved");
        migration(root, evidence, alex, sam);
        moderation(root, alex, sam, mia);
    }

    private static void migration(Path root, String evidence, Identity alex, Identity sam) throws Exception {
        Path file = root.resolve("legacy.json");
        String legacy = evidence.replace("\"schema\": 2", "\"schema\": 1");
        Files.writeString(file, legacy);
        var store = new RatingStore(file);
        store.validate();
        check(Files.readString(file).equals(legacy) && !Files.exists(root.resolve("legacy.json.v1.bak")), "Read-only migration validation must preserve its source");
        store.initialize();
        check(Files.readString(root.resolve("legacy.json.v1.bak")).equals(legacy), "Five-star migration must back up the exact old database");
        Review migrated = store.profile(alex.id(), sam.id()).myReview();
        check(migrated.ingenuity() == 3 && migrated.skill() == 5 && migrated.friendliness() == 5 && migrated.revision() == 2,
                "Legacy scores must map 1/2/3 to 1/3/5 without changing revision");
        var report = store.reports().getFirst();
        check(report.snapshot().skill() == 5 && report.snapshot().revision() == 2 && report.status().equals("pending"), "Report evidence and status must survive the score migration");
        check(store.profile(sam.id(), sam.id()).summary().skill() == 3, "Migrated averages must use the five-star scale and preserve one-star scores");
        new RatingStore(file).initialize();
        check(new RatingStore(file).profile(alex.id(), sam.id()).myReview().skill() == 5, "Migration must never run twice");
        store.save(alex, sam, new Integer[] {5, 4, null}, "Five stars");
        check(new RatingStore(file).profile(alex.id(), sam.id()).myReview().skill() == 4, "New four- and five-star ratings must survive reopening");
    }

    private static void moderation(Path root, Identity alex, Identity sam, Identity mia) {
        var store = new RatingStore(root.resolve("moderation.json"));
        String id = store.save(alex, sam, new Integer[] {5, 4, 5}, "Original evidence").get("id").getAsString();
        String first = store.report(sam.id(), id, "unfounded", "First report").get("id").getAsString();
        check(store.reportPage(true, 999).pending() == 1 && store.reportPage(true, 999).page() == 0, "The operator list must clamp pages and count pending reports");
        check(store.reportDetails(first).snapshot().comment().equals("Original evidence") && store.reportDetails(first).reporterName().equals("Sam"), "Operators must receive the report, reporter and full evidence");
        store.save(alex, sam, new Integer[] {4, 4, 4}, "Edited");
        check(store.reportDetails(first).outdated(), "Edited evidence must be marked as outdated");
        expect("review_changed", () -> store.moderate(first, true, "Operator", ""));
        check(store.reportDetails(first).status().equals("pending") && store.profile(sam.id(), sam.id()).summary().count() == 1, "A stale removal decision must not close the report or delete the new review");
        store.moderate(first, false, "Operator", "Older revision");
        check(store.reportPage(false, 0).resolved() == 1 && store.reportDetails(first).decisionNote().equals("Operator")
                && store.reportDetails(first).decisionReason().equals("Older revision"), "Kept reports must archive the operator and reason separately");
        expect("report_resolved", () -> store.moderate(first, true, "OtherOperator", ""));
        String second = store.report(sam.id(), id, "abuse", "Current report").get("id").getAsString();
        store.moderate(second, true, "Operator", "");
        check(store.reportDetails(second).status().equals("removed") && store.profile(sam.id(), sam.id()).summary().count() == 0, "Removal must archive the report and exclude the review from reputation");
        String replacement = store.save(alex, sam, new Integer[] {5, 5, 4}, "Fresh review after removal").get("id").getAsString();
        check(!replacement.equals(id) && store.profile(alex.id(), sam.id()).myReview().id().equals(replacement),
                "Operator removal must allow the same author to rate the same player again");
        expect("report_resolved", () -> store.moderate(second, true, "Operator", "Old decision"));
        check(store.profile(sam.id(), sam.id()).summary().count() == 1, "An archived removal must never affect a new review");
        String third = store.report(sam.id(), replacement, "other", "New review report").get("id").getAsString();
        check(!third.equals(second), "The new review must have an independent complaint");
        store.moderate(third, true, "Operator", "");
        String next = store.save(alex, sam, new Integer[] {4, 5, 5}, "Another review").get("id").getAsString();
        check(!next.equals(replacement) && store.profile(sam.id(), sam.id()).summary().count() == 1,
                "Repeated removals must not become a permanent ban or restore previously removed reviews");
        for (int i = 0; i < 5; i++) {
            var author = new Identity(new UUID(1, i), "Author" + i);
            String review = store.save(author, mia, new Integer[] {5, null, 4}, "Page " + i).get("id").getAsString();
            store.report(mia.id(), review, "other", "");
        }
        var page = store.reportPage(true, 0);
        check(page.pending() == 5 && page.pages() == 3 && page.reports().size() == 2, "The operator list must send at most two reports per page");
        check(store.reportPage(true, 2).reports().size() == 1 && store.reportPage(false, 0).reports().size() == 2, "Pagination and the resolved archive must remain separate");
        expect("report_not_found", () -> store.reportDetails(UUID.randomUUID().toString()));
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void expect(String code, Runnable operation) {
        try { operation.run(); }
        catch (RuntimeException error) {
            check(RatingException.errorCode(error).equals(code), "Expected " + code + ", got " + RatingException.errorCode(error));
            return;
        }
        throw new AssertionError("Expected " + code);
    }
}
