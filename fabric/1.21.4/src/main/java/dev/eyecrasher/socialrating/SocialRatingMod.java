package dev.eyecrasher.socialrating;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import static dev.eyecrasher.socialrating.RatingData.*;

public final class SocialRatingMod implements ModInitializer {
    private static final Gson JSON = new Gson();
    private static final String ADMIN_MENU = "/socialrating admin";
    private final Map<MinecraftServer, ServerFiles> files = new HashMap<>();
    private record Window(long second, int count) {}
    private record Result(Object data, int pending, boolean notifyOperators, boolean changed) {}
    private final Map<UUID, Window> requests = new HashMap<>();

    @Override public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(RatingWire.Request.TYPE, RatingWire.Request.CODEC);
        PayloadTypeRegistry.playS2C().register(RatingWire.Reply.TYPE, RatingWire.Reply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RatingWire.Request.TYPE, (payload, context) ->
                context.server().execute(() -> handle(context.server(), context.player(), payload)));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try { files(server); }
            catch (RuntimeException error) { org.slf4j.LoggerFactory.getLogger("socialrating").error("Cannot initialize Social Rating host files", error); }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { var active = files.remove(server); if (active != null) active.close(); requests.clear(); });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> requests.remove(handler.player.getUUID()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                if (isOperator(handler.player)) {
                    var active = files(server);
                    if (active.rules().remindOperatorsOnJoin) active.submit(() -> active.store().reportPage(true, 0).pending())
                            .thenAccept(pending -> server.execute(() -> { if (isOperator(handler.player)) notifyOperator(server, handler.player, pending); }));
                }
            } catch (RatingException error) { org.slf4j.LoggerFactory.getLogger("socialrating").warn("Cannot check pending reports on join", error); }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
                Commands.literal("socialrating").executes(context -> openMenu(context.getSource(), false))
                        .then(Commands.literal("admin").requires(this::canModerate)
                                .executes(context -> openMenu(context.getSource(), true))
                                .then(Commands.literal("reload").executes(context -> reload(context.getSource())))
                                .then(Commands.literal("backup").executes(context -> backup(context.getSource())))
                                .then(Commands.literal("reports").executes(context -> {
                                    var source = context.getSource(); var active = files(source.getServer());
                                    active.submit(() -> active.store().reports().stream().filter(report -> report.status().equals("pending")).toList()).whenComplete((reports, error) -> source.getServer().execute(() -> {
                                        if (error != null) source.sendFailure(message(source.getServer(), "error." + RatingException.errorCode(error)));
                                        else {
                                            source.sendSuccess(() -> message(source.getServer(), "admin.pending", reports.size()), false);
                                            for (var report : reports.stream().limit(20).toList()) source.sendSuccess(() -> message(source.getServer(), "admin.report", report.id(),
                                                    message(source.getServer(), "reason." + report.reason()), report.snapshot().authorName(), report.snapshot().targetName(),
                                                    report.snapshot().comment(), report.details()), false);
                                        }
                                    })); return 1;
                                }))
                                .then(decisionCommand("keep", false)).then(decisionCommand("remove", true)))));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> decisionCommand(String name, boolean remove) {
        return Commands.literal(name).then(Commands.argument("id", StringArgumentType.word())
                .executes(context -> resolve(context.getSource(), StringArgumentType.getString(context, "id"), remove, ""))
                .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(context -> resolve(context.getSource(), StringArgumentType.getString(context, "id"), remove, StringArgumentType.getString(context, "reason")))));
    }

    private int reload(CommandSourceStack source) {
        var active = files(source.getServer());
        return background(source, () -> {
            try { active.reload(); }
            catch (java.io.IOException error) { throw new RatingException("config_invalid", error); }
            source.getServer().execute(() -> {
                signal(source.getServer(), "{\"translationsChanged\":true,\"rulesChanged\":true}");
                for (var player : source.getServer().getPlayerList().getPlayers()) source.getServer().getCommands().sendCommands(player);
            });
            return "admin.reloaded";
        });
    }

    private int background(CommandSourceStack source, Supplier<String> work) {
        files(source.getServer()).submit(work).whenComplete((key, error) -> source.getServer().execute(() -> {
            if (error == null) source.sendSuccess(() -> message(source.getServer(), key), false);
            else source.sendFailure(message(source.getServer(), "error." + RatingException.errorCode(error)));
        })); return 1;
    }

    private int backup(CommandSourceStack source) {
        var store = files(source.getServer()).store();
        return background(source, () -> { store.backup(); return "admin.backed_up"; });
    }

    private int resolve(CommandSourceStack source, String id, boolean remove, String reason) {
        var active = files(source.getServer());
        return background(source, () -> {
            active.store().moderate(id, remove, source.getTextName(), reason);
            int pending = active.store().reportPage(true, 0).pending();
            source.getServer().execute(() -> reportsChanged(source.getServer(), pending, false));
            return remove ? "admin.removed" : "admin.kept";
        });
    }

    private int openMenu(CommandSourceStack source, boolean admin) {
        var player = source.getPlayer();
        if (player == null) { source.sendSuccess(() -> message(source.getServer(), "command.usage", ADMIN_MENU), false); return 1; }
        if (!ServerPlayNetworking.canSend(player, RatingWire.Reply.TYPE)) { source.sendFailure(message(source.getServer(), "command.client_required")); return 0; }
        ServerPlayNetworking.send(player, new RatingWire.Reply(0, admin ? "{\"openAdmin\":true}" : "{\"openProfile\":true}"));
        return 1;
    }

    private ServerFiles files(MinecraftServer server) {
        return files.computeIfAbsent(server, value -> {
            try { return new ServerFiles(FabricLoader.getInstance().getConfigDir(), value.getWorldPath(LevelResource.ROOT).resolve("data/socialrating.json")); }
            catch (java.io.IOException error) { throw new RatingException("storage_unavailable", error); }
        });
    }

    private boolean isOperator(ServerPlayer player) { return canModerate(player.createCommandSourceStack()); }
    private boolean canModerate(CommandSourceStack source) {
        // Minecraft probes command requirements with a synthetic source that has no server.
        var active = files.get(source.getServer());
        var permission = switch (active == null ? 2 : active.rules().moderatorPermissionLevel) {
            case 1 -> Commands.LEVEL_MODERATORS; case 3 -> Commands.LEVEL_ADMINS; case 4 -> Commands.LEVEL_OWNERS; default -> Commands.LEVEL_GAMEMASTERS;
        };
        return source.hasPermission(permission);
    }

    private void notifyOperator(MinecraftServer server, ServerPlayer player, int pending) {
        if (pending == 0 || !files(server).rules().notifyOperators) return;
        player.sendSystemMessage(message(server, "admin.notification_menu", pending, ADMIN_MENU).copy().withColor(0xffd575)
                .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, ADMIN_MENU))));
    }

    private void reportsChanged(MinecraftServer server, int pending, boolean notify) {
        for (var player : server.getPlayerList().getPlayers()) if (isOperator(player) && notify) notifyOperator(server, player, pending);
        signal(server, "{\"reportsChanged\":true}");
    }

    private void signal(MinecraftServer server, String json) {
        for (var player : server.getPlayerList().getPlayers()) if (ServerPlayNetworking.canSend(player, RatingWire.Reply.TYPE))
            ServerPlayNetworking.send(player, new RatingWire.Reply(0, json));
    }

    private Component message(MinecraftServer server, String key, Object... args) {
        String full = "socialrating." + key;
        ServerFiles active = files.get(server);
        String fallback = active == null ? ServerFiles.defaultText(full) : active.translations(null).values().getOrDefault(full, full);
        return Component.translatableWithFallback(full, fallback, args);
    }

    /** World/permission checks run on the server thread; disk work runs on a bounded, serial worker. */
    private void handle(MinecraftServer server, ServerPlayer player, RatingWire.Request request) {
        try {
            var active = files(server); var rules = active.rules();
            long second = System.nanoTime() / 1_000_000_000L;
            Window window = requests.get(player.getUUID());
            int count = window != null && window.second == second ? window.count + 1 : 1;
            requests.put(player.getUUID(), new Window(second, count));
            if (count > rules.requestsPerSecond) throw new RatingException("too_many_requests");
            if (request.action().startsWith("admin_") && !isOperator(player)) throw new RatingException("forbidden");
            JsonObject body = JsonParser.parseString(request.json()).getAsJsonObject();
            UUID viewer = player.getUUID(); var store = active.store();
            Supplier<Object> work = switch (request.action()) {
                case "rules" -> active::rules;
                case "translations" -> () -> active.translations(body.get("language").getAsString());
                case "profile" -> () -> store.profile(viewer, UUID.fromString(body.get("target").getAsString()));
                case "summaries" -> {
                    var ids = body.getAsJsonArray("ids");
                    if (ids.size() > 64) throw new RatingException("too_many_players");
                    var targets = ids.asList().stream().map(id -> UUID.fromString(id.getAsString())).toList();
                    yield () -> store.summaries(targets);
                }
                case "save" -> {
                    UUID targetId = UUID.fromString(body.get("target").getAsString());
                    ServerPlayer target = server.getPlayerList().getPlayer(targetId);
                    if (targetId.equals(viewer)) throw new RatingException("self_rating");
                    if (target == null || target.level() != player.level() || target.isSpectator() || target.isInvisibleTo(player)
                            || target.distanceToSqr(player) > rules.ratingDistance * rules.ratingDistance
                            || (rules.requireLineOfSight && !player.hasLineOfSight(target))) throw new RatingException("not_nearby");
                    Integer[] scores = new Integer[3]; String[] categories = {"ingenuity", "skill", "friendliness"};
                    for (int i = 0; i < 3; i++) {
                        var value = body.get(categories[i]);
                        if (value != null && !value.isJsonNull()) {
                            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.getAsString().matches("[1-5]")) throw new RatingException("invalid_score");
                            scores[i] = value.getAsInt();
                        }
                    }
                    var authorIdentity = new Identity(viewer, player.getGameProfile().getName());
                    var targetIdentity = new Identity(targetId, target.getGameProfile().getName());
                    yield () -> store.save(authorIdentity, targetIdentity, scores, body.get("comment").getAsString());
                }
                case "withdraw" -> () -> store.withdraw(viewer, body.get("review").getAsString());
                case "report" -> () -> store.report(viewer, body.get("review").getAsString(), body.get("reason").getAsString(), body.get("details").getAsString());
                case "my_reports" -> () -> store.reportPage(viewer, body.get("pending").getAsBoolean(), body.get("page").getAsInt());
                case "my_report" -> () -> store.reportDetails(viewer, body.get("id").getAsString());
                case "admin_reports" -> () -> store.reportPage(body.get("pending").getAsBoolean(), body.get("page").getAsInt());
                case "admin_report" -> () -> store.reportDetails(body.get("id").getAsString());
                case "admin_resolve" -> {
                    String operator = player.getGameProfile().getName();
                    yield () -> store.moderate(body.get("id").getAsString(), body.get("remove").getAsBoolean(), operator, body.get("note").getAsString());
                }
                default -> throw new RatingException("invalid_body");
            };
            active.submit(() -> {
                Object result = work.get();
                boolean report = request.action().equals("report");
                boolean changed = report || request.action().equals("withdraw") || request.action().equals("admin_resolve") || request.action().equals("save");
                boolean notify = report && ((JsonObject) result).get("created").getAsBoolean();
                return new Result(result, changed ? store.reportPage(true, 0).pending() : 0, notify, changed);
            }).whenComplete((result, error) -> server.execute(() -> {
                if (result != null && result.changed()) reportsChanged(server, result.pending(), result.notifyOperators());
                if (server.getPlayerList().getPlayer(viewer) == player) reply(player, request.id(), result == null ? null : result.data(), error);
            }));
        } catch (RuntimeException error) { reply(player, request.id(), null, error); }
    }

    private void reply(ServerPlayer player, long id, Object data, Throwable error) {
        var reply = new JsonObject();
        if (error != null) reply.addProperty("error", error instanceof RatingException || error instanceof java.util.concurrent.CompletionException
                ? RatingException.errorCode(error) : "invalid_body");
        else reply.add("data", JSON.toJsonTree(data));
        String encoded = JSON.toJson(reply);
        if (encoded.length() > 262144) encoded = "{\"error\":\"response_too_large\"}";
        if (ServerPlayNetworking.canSend(player, RatingWire.Reply.TYPE)) ServerPlayNetworking.send(player, new RatingWire.Reply(id, encoded));
    }
}
