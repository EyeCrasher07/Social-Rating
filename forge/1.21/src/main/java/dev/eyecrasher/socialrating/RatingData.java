package dev.eyecrasher.socialrating;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.UUID;
import java.util.Map;

public final class RatingData {
    public static final int MAX_SCORE = 5;
    private RatingData() {}

    public record Identity(UUID id, String name) {}
    public record Translations(String language, Map<String, String> values) {}
    public record ModerationReport(String id, String reporterName, String reason, String details,
                                   Review snapshot, String createdAt, String status, String decisionNote,
                                   boolean outdated, String decisionReason) {}
    public record ReportPage(List<ModerationReport> reports, int page, int pages, int pending, int resolved) {}

    public record Summary(String id, int count, Double average, Double ingenuity, Double skill,
                          Double friendliness, @SerializedName("ingenuity_count") int ingenuityCount,
                          @SerializedName("skill_count") int skillCount,
                          @SerializedName("friendliness_count") int friendlinessCount) {}

    public record Review(String id, String author, String target,
                         @SerializedName("author_name") String authorName,
                         @SerializedName("target_name") String targetName,
                         Integer ingenuity, Integer skill, Integer friendliness, String comment,
                         @SerializedName("updated_at") String updatedAt, int revision,
                         @SerializedName("report_status") String reportStatus) {}

    public record Profile(String id, String name, Summary summary, List<Review> reviews, List<Review> sent,
                          @SerializedName("my_review") Review myReview,
                          @SerializedName("review_count") int reviewCount,
                          @SerializedName("sent_count") int sentCount) {}
}
