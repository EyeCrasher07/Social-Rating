package dev.eyecrasher.socialrating;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.UUID;
import java.util.Map;

public final class RatingData {
    public static final int MAX_SCORE = 5;
    private RatingData() {}

    public static final class Identity {
        private final UUID id;
        private final String name;
        public Identity(UUID id, String name) {
            this.id = id;
            this.name = name;
        }
        public UUID id() { return id; }
        public String name() { return name; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            Identity that = (Identity) other;
            return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, name); }
}
    public static final class Translations {
        private final String language;
        private final Map<String, String> values;
        public Translations(String language, Map<String, String> values) {
            this.language = language;
            this.values = values;
        }
        public String language() { return language; }
        public Map<String, String> values() { return values; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            Translations that = (Translations) other;
            return java.util.Objects.equals(language, that.language) && java.util.Objects.equals(values, that.values);
        }
        @Override public int hashCode() { return java.util.Objects.hash(language, values); }
}
    public static final class ModerationReport {
        private final String id;
        private final String reporterName;
        private final String reason;
        private final String details;
        private final Review snapshot;
        private final String createdAt;
        private final String status;
        private final String decisionNote;
        private final boolean outdated;
        private final String decisionReason;
        public ModerationReport(String id, String reporterName, String reason, String details, Review snapshot, String createdAt, String status, String decisionNote, boolean outdated, String decisionReason) {
            this.id = id;
            this.reporterName = reporterName;
            this.reason = reason;
            this.details = details;
            this.snapshot = snapshot;
            this.createdAt = createdAt;
            this.status = status;
            this.decisionNote = decisionNote;
            this.outdated = outdated;
            this.decisionReason = decisionReason;
        }
        public String id() { return id; }
        public String reporterName() { return reporterName; }
        public String reason() { return reason; }
        public String details() { return details; }
        public Review snapshot() { return snapshot; }
        public String createdAt() { return createdAt; }
        public String status() { return status; }
        public String decisionNote() { return decisionNote; }
        public boolean outdated() { return outdated; }
        public String decisionReason() { return decisionReason; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            ModerationReport that = (ModerationReport) other;
            return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(reporterName, that.reporterName) && java.util.Objects.equals(reason, that.reason) && java.util.Objects.equals(details, that.details) && java.util.Objects.equals(snapshot, that.snapshot) && java.util.Objects.equals(createdAt, that.createdAt) && java.util.Objects.equals(status, that.status) && java.util.Objects.equals(decisionNote, that.decisionNote) && java.util.Objects.equals(outdated, that.outdated) && java.util.Objects.equals(decisionReason, that.decisionReason);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, reporterName, reason, details, snapshot, createdAt, status, decisionNote, outdated, decisionReason); }
}
    public static final class ReportPage {
        private final List<ModerationReport> reports;
        private final int page;
        private final int pages;
        private final int pending;
        private final int resolved;
        public ReportPage(List<ModerationReport> reports, int page, int pages, int pending, int resolved) {
            this.reports = reports;
            this.page = page;
            this.pages = pages;
            this.pending = pending;
            this.resolved = resolved;
        }
        public List<ModerationReport> reports() { return reports; }
        public int page() { return page; }
        public int pages() { return pages; }
        public int pending() { return pending; }
        public int resolved() { return resolved; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            ReportPage that = (ReportPage) other;
            return java.util.Objects.equals(reports, that.reports) && java.util.Objects.equals(page, that.page) && java.util.Objects.equals(pages, that.pages) && java.util.Objects.equals(pending, that.pending) && java.util.Objects.equals(resolved, that.resolved);
        }
        @Override public int hashCode() { return java.util.Objects.hash(reports, page, pages, pending, resolved); }
}

    public static final class Summary {
        private final String id;
        private final int count;
        private final Double average;
        private final Double ingenuity;
        private final Double skill;
        private final Double friendliness;
        @SerializedName("ingenuity_count") private final int ingenuityCount;
        @SerializedName("skill_count") private final int skillCount;
        @SerializedName("friendliness_count") private final int friendlinessCount;
        public Summary(String id, int count, Double average, Double ingenuity, Double skill, Double friendliness, int ingenuityCount, int skillCount, int friendlinessCount) {
            this.id = id;
            this.count = count;
            this.average = average;
            this.ingenuity = ingenuity;
            this.skill = skill;
            this.friendliness = friendliness;
            this.ingenuityCount = ingenuityCount;
            this.skillCount = skillCount;
            this.friendlinessCount = friendlinessCount;
        }
        public String id() { return id; }
        public int count() { return count; }
        public Double average() { return average; }
        public Double ingenuity() { return ingenuity; }
        public Double skill() { return skill; }
        public Double friendliness() { return friendliness; }
        public int ingenuityCount() { return ingenuityCount; }
        public int skillCount() { return skillCount; }
        public int friendlinessCount() { return friendlinessCount; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            Summary that = (Summary) other;
            return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(count, that.count) && java.util.Objects.equals(average, that.average) && java.util.Objects.equals(ingenuity, that.ingenuity) && java.util.Objects.equals(skill, that.skill) && java.util.Objects.equals(friendliness, that.friendliness) && java.util.Objects.equals(ingenuityCount, that.ingenuityCount) && java.util.Objects.equals(skillCount, that.skillCount) && java.util.Objects.equals(friendlinessCount, that.friendlinessCount);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, count, average, ingenuity, skill, friendliness, ingenuityCount, skillCount, friendlinessCount); }
}

    public static final class Review {
        private final String id;
        private final String author;
        private final String target;
        @SerializedName("author_name") private final String authorName;
        @SerializedName("target_name") private final String targetName;
        private final Integer ingenuity;
        private final Integer skill;
        private final Integer friendliness;
        private final String comment;
        @SerializedName("updated_at") private final String updatedAt;
        private final int revision;
        @SerializedName("report_status") private final String reportStatus;
        public Review(String id, String author, String target, String authorName, String targetName, Integer ingenuity, Integer skill, Integer friendliness, String comment, String updatedAt, int revision, String reportStatus) {
            this.id = id;
            this.author = author;
            this.target = target;
            this.authorName = authorName;
            this.targetName = targetName;
            this.ingenuity = ingenuity;
            this.skill = skill;
            this.friendliness = friendliness;
            this.comment = comment;
            this.updatedAt = updatedAt;
            this.revision = revision;
            this.reportStatus = reportStatus;
        }
        public String id() { return id; }
        public String author() { return author; }
        public String target() { return target; }
        public String authorName() { return authorName; }
        public String targetName() { return targetName; }
        public Integer ingenuity() { return ingenuity; }
        public Integer skill() { return skill; }
        public Integer friendliness() { return friendliness; }
        public String comment() { return comment; }
        public String updatedAt() { return updatedAt; }
        public int revision() { return revision; }
        public String reportStatus() { return reportStatus; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            Review that = (Review) other;
            return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(author, that.author) && java.util.Objects.equals(target, that.target) && java.util.Objects.equals(authorName, that.authorName) && java.util.Objects.equals(targetName, that.targetName) && java.util.Objects.equals(ingenuity, that.ingenuity) && java.util.Objects.equals(skill, that.skill) && java.util.Objects.equals(friendliness, that.friendliness) && java.util.Objects.equals(comment, that.comment) && java.util.Objects.equals(updatedAt, that.updatedAt) && java.util.Objects.equals(revision, that.revision) && java.util.Objects.equals(reportStatus, that.reportStatus);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, author, target, authorName, targetName, ingenuity, skill, friendliness, comment, updatedAt, revision, reportStatus); }
}

    public static final class Profile {
        private final String id;
        private final String name;
        private final Summary summary;
        private final List<Review> reviews;
        private final List<Review> sent;
        @SerializedName("my_review") private final Review myReview;
        @SerializedName("review_count") private final int reviewCount;
        @SerializedName("sent_count") private final int sentCount;
        public Profile(String id, String name, Summary summary, List<Review> reviews, List<Review> sent, Review myReview, int reviewCount, int sentCount) {
            this.id = id;
            this.name = name;
            this.summary = summary;
            this.reviews = reviews;
            this.sent = sent;
            this.myReview = myReview;
            this.reviewCount = reviewCount;
            this.sentCount = sentCount;
        }
        public String id() { return id; }
        public String name() { return name; }
        public Summary summary() { return summary; }
        public List<Review> reviews() { return reviews; }
        public List<Review> sent() { return sent; }
        public Review myReview() { return myReview; }
        public int reviewCount() { return reviewCount; }
        public int sentCount() { return sentCount; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            Profile that = (Profile) other;
            return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(name, that.name) && java.util.Objects.equals(summary, that.summary) && java.util.Objects.equals(reviews, that.reviews) && java.util.Objects.equals(sent, that.sent) && java.util.Objects.equals(myReview, that.myReview) && java.util.Objects.equals(reviewCount, that.reviewCount) && java.util.Objects.equals(sentCount, that.sentCount);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, name, summary, reviews, sent, myReview, reviewCount, sentCount); }
}
}
