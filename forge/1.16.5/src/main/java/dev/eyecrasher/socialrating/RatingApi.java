package dev.eyecrasher.socialrating;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

import static dev.eyecrasher.socialrating.RatingData.*;

/** Requests go over the current Minecraft connection. The client never opens a database. */
public final class RatingApi {
    private static final Gson JSON = new Gson();
    private static final Type SUMMARIES = new TypeToken<Map<String, Summary>>() {}.getType();
    private static final class Pending  {
        private final CompletableFuture<?> future;
        private final Type type;
        private final long deadline;
        public Pending(CompletableFuture<?> future, Type type, long deadline) {
            this.future = future;
            this.type = type;
            this.deadline = deadline;
        }
        public CompletableFuture<?> future() { return future; }
        public Type type() { return type; }
        public long deadline() { return deadline; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            Pending that = (Pending) other;
            return java.util.Objects.equals(future, that.future) && java.util.Objects.equals(type, that.type) && java.util.Objects.equals(deadline, that.deadline);
        }
        @Override public int hashCode() { return java.util.Objects.hash(future, type, deadline); }
}
    private final Map<Long, Pending> pending = new HashMap<>();
    private long nextId;
    public CompletableFuture<RatingRules> rules() { return request("rules", new JsonObject(), RatingRules.class); }

    public CompletableFuture<Translations> translations(String language) {
        var body = new JsonObject(); body.addProperty("language", language);
        return request("translations", body, Translations.class);
    }

    public CompletableFuture<Profile> profile(UUID viewer, UUID target) {
        var body = new JsonObject(); body.addProperty("target", target.toString());
        return request("profile", body, Profile.class);
    }

    public CompletableFuture<Map<String, Summary>> summaries(UUID viewer, List<UUID> targets) {
        var body = new JsonObject(); body.add("ids", JSON.toJsonTree(targets));
        return request("summaries", body, SUMMARIES);
    }

    public CompletableFuture<JsonObject> save(Identity author, Identity target, Integer[] scores, String comment) {
        var body = new JsonObject(); body.addProperty("target", target.id().toString());
        String[] keys = {"ingenuity", "skill", "friendliness"};
        for (int i = 0; i < 3; i++) body.add(keys[i], JSON.toJsonTree(scores[i]));
        body.addProperty("comment", comment);
        return request("save", body, JsonObject.class);
    }

    public CompletableFuture<JsonObject> report(UUID reporter, String review, String reason, String details) {
        var body = new JsonObject(); body.addProperty("review", review);
        body.addProperty("reason", reason); body.addProperty("details", details);
        return request("report", body, JsonObject.class);
    }

    public CompletableFuture<ReportPage> reports(boolean pending, int page) {
        return reports(true, pending, page);
    }
    public CompletableFuture<ReportPage> reports(boolean admin, boolean pending, int page) {
        var body = new JsonObject(); body.addProperty("pending", pending); body.addProperty("page", page);
        return request(admin ? "admin_reports" : "my_reports", body, ReportPage.class);
    }

    public CompletableFuture<ModerationReport> reportDetails(String id) {
        return reportDetails(true, id);
    }
    public CompletableFuture<ModerationReport> reportDetails(boolean admin, String id) {
        var body = new JsonObject(); body.addProperty("id", id);
        return request(admin ? "admin_report" : "my_report", body, ModerationReport.class);
    }

    public CompletableFuture<JsonObject> withdraw(String review) {
        var body = new JsonObject(); body.addProperty("review", review);
        return request("withdraw", body, JsonObject.class);
    }

    public CompletableFuture<ModerationReport> resolve(String id, boolean remove, String note) {
        var body = new JsonObject(); body.addProperty("id", id); body.addProperty("remove", remove); body.addProperty("note", note);
        return request("admin_resolve", body, ModerationReport.class);
    }

    private <T> CompletableFuture<T> request(String action, JsonObject body, Type type) {
        var result = new CompletableFuture<T>();
        var mc = Minecraft.getInstance();
        if (mc.getConnection() == null || !RatingClientPlatform.canSend()) {
            mc.execute(() -> result.completeExceptionally(new RatingException("server_missing")));
            return result;
        }
        long id = ++nextId;
        pending.put(id, new Pending(result, type, System.nanoTime() + 8_000_000_000L));
        RatingClientPlatform.send(new RatingWire.Request(id, action, JSON.toJson(body)));
        return result;
    }

    @SuppressWarnings("unchecked")
    public void receive(RatingWire.Reply packet) {
        Pending request = pending.remove(packet.id());
        if (request == null) return;
        try {
            var reply = JSON.fromJson(packet.json(), JsonObject.class);
            if (reply.has("error")) request.future.completeExceptionally(new RatingException(reply.get("error").getAsString()));
            else ((CompletableFuture<Object>) request.future).complete(JSON.fromJson(reply.get("data"), request.type));
        } catch (RuntimeException error) { request.future.completeExceptionally(new RatingException("service_error", error)); }
    }

    public void tick() {
        var expired = pending.entrySet().stream().filter(entry -> entry.getValue().deadline < System.nanoTime()).map(Map.Entry::getKey).collect(java.util.stream.Collectors.toList());
        for (long id : expired) pending.remove(id).future.completeExceptionally(new RatingException("unavailable"));
    }

    public void disconnect() {
        var requests = dev.eyecrasher.socialrating.JavaCompat.copyList(pending.values());
        pending.clear();
        for (var request : requests) request.future.completeExceptionally(new RatingException("unavailable"));
    }
}
