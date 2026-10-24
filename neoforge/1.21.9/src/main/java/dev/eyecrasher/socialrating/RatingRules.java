package dev.eyecrasher.socialrating;

import java.io.IOException;

/** Server rules shared with clients; fixed protocol and five-star scale are not configurable. */
public class RatingRules {
    public boolean ratingsEnabled = true;
    public boolean reportsEnabled = true;
    public boolean commentsEnabled = true;
    public boolean allowEditing = true;
    public boolean allowWithdrawal = true;
    public boolean allowSkippedCategories = true;
    public boolean ingenuityEnabled = true;
    public boolean skillEnabled = true;
    public boolean friendlinessEnabled = true;
    public double ratingDistance = 6;
    public boolean requireLineOfSight = true;
    public int maxCommentLength = 300;
    public int maxReportDetailsLength = 300;
    public int maxDecisionReasonLength = 200;
    public boolean nameplatesEnabled = true;
    public boolean interactionHintEnabled = true;
    public int maxNameplateDistance = 64;
    public int summaryRefreshTicks = 100;
    public int profileReviewLimit = 100;
    public int requestsPerSecond = 20;
    public int moderatorPermissionLevel = 2;
    public boolean notifyOperators = true;
    public boolean remindOperatorsOnJoin = true;
    public boolean backupsEnabled = true;
    public boolean backupOnStartup = true;
    public int backupIntervalMinutes = 30;
    public int backupRetention = 10;

    public boolean categoryEnabled(int category) {
        return switch (category) { case 0 -> ingenuityEnabled; case 1 -> skillEnabled; default -> friendlinessEnabled; };
    }

    public void validate() throws IOException {
        if (!Double.isFinite(ratingDistance) || ratingDistance < 1 || ratingDistance > 64) throw new IOException("ratingDistance must be 1..64");
        if (!ingenuityEnabled && !skillEnabled && !friendlinessEnabled) throw new IOException("Enable at least one rating category");
        range("maxCommentLength", maxCommentLength, 1, 1000);
        range("maxReportDetailsLength", maxReportDetailsLength, 1, 1000);
        range("maxDecisionReasonLength", maxDecisionReasonLength, 1, 500);
        range("maxNameplateDistance", maxNameplateDistance, 8, 64);
        range("summaryRefreshTicks", summaryRefreshTicks, 20, 1200);
        range("profileReviewLimit", profileReviewLimit, 1, 100);
        range("requestsPerSecond", requestsPerSecond, 4, 1000);
        range("moderatorPermissionLevel", moderatorPermissionLevel, 1, 4);
        range("backupIntervalMinutes", backupIntervalMinutes, 1, 10080);
        range("backupRetention", backupRetention, 1, 100);
    }

    private static void range(String key, int value, int min, int max) throws IOException {
        if (value < min || value > max) throw new IOException(key + " must be " + min + ".." + max);
    }
}
