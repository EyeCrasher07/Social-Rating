package dev.eyecrasher.socialrating;

import com.mojang.blaze3d.platform.InputConstants;
import dev.eyecrasher.socialrating.ui.NearbyScreen;
import dev.eyecrasher.socialrating.ui.ProfileScreen;
import dev.eyecrasher.socialrating.ui.RatingScreen;
import dev.eyecrasher.socialrating.ui.AdminReportsScreen;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

import static dev.eyecrasher.socialrating.RatingData.*;

public final class SocialRatingClient {
    public static SocialRatingClient INSTANCE;
    private RatingConfig config;
    private RatingApi api;
    private KeyMapping profileKey;
    private volatile Map<String, Summary> summaries = Map.of();
    private Object world;
    private long generation;
    private boolean refreshing;
    private int untilRefresh;
    private boolean openRequested;
    private boolean adminRequested;
    private RatingRules rules = new RatingRules();
    private boolean rulesRequested, rulesPending;
    private long rulesEpoch;
    private final RatingLanguage language = new RatingLanguage();
    private String requestedLanguage = "";
    private boolean languagePending;
    private int untilLanguage;
    private long languageEpoch;

    public void initialize() {
        INSTANCE = this;
        config = RatingConfig.load();
        api = new RatingApi();
        RatingClientPlatform.replyReceiver((packet, mc) -> mc.execute(() -> {
            if (packet.id() != 0) { api.receive(packet); return; }
            var signal = com.google.gson.JsonParser.parseString(packet.json()).getAsJsonObject();
            if (signal.has("translationsChanged")) { requestedLanguage = ""; untilLanguage = 0; }
            if (signal.has("rulesChanged")) rulesRequested = false;
            if (signal.has("openProfile")) { applyConfig(RatingConfig.load()); openRequested = true; }
            if (signal.has("openAdmin")) adminRequested = true;
            if (signal.has("reportsChanged")) {
                invalidate();
                if (mc.screen instanceof RatingScreen screen) screen.reportsChanged();
            }
        }));
        RatingClientPlatform.joined(mc -> { requestedLanguage = ""; untilLanguage = 0; rulesRequested = false; });
        RatingClientPlatform.disconnected(mc -> {
            languageEpoch++; languagePending = false; requestedLanguage = "";
            language.clear(); api.disconnect(); invalidate();
            adminRequested = false;
            rulesEpoch++; rulesPending = false; rulesRequested = false; rules = new RatingRules();
        });
        String category = "key.category.socialrating.general";
        profileKey = RatingClientPlatform.key(new KeyMapping("key.socialrating.profile", InputConstants.KEY_R, category));
        RatingClientPlatform.tick(this::tick);
    }

    public void renderHint(net.minecraft.client.gui.GuiGraphics graphics) {
        var mc = Minecraft.getInstance();
        if (!config.showInteractionHint || !rules.interactionHintEnabled || mc.screen != null) return;
        Player target = aimedPlayer();
        if (target != null) graphics.drawCenteredString(mc.font, tr("hint", profileKey.getTranslatedKeyMessage(), target.getGameProfile().getName()),
                graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 18, 0xffffd575);
    }

    private void tick(Minecraft mc) {
        api.tick();
        language.tick();
        if (world != mc.level) {
            world = mc.level;
            generation++;
            summaries = Map.of();
            refreshing = false;
            untilRefresh = 0;
        }
        boolean clicked = false;
        while (profileKey.consumeClick()) clicked = true;
        if ((clicked || openRequested) && mc.player != null && mc.screen == null) {
            openRequested = false;
            Player target = aimedPlayer();
            openProfile(null, target == null ? self() : identity(target));
        }
        if (mc.player == null || mc.level == null) { openRequested = false; return; }
        if (!rulesRequested && !rulesPending) refreshRules();
        if (adminRequested) {
            adminRequested = false;
            mc.setScreen(new AdminReportsScreen(null, this));
        }
        if (--untilLanguage <= 0 && !languagePending && !preferredLanguage().equals(requestedLanguage)) refreshLanguage();
        if (--untilRefresh <= 0 && !refreshing) refreshSummaries();
    }

    private void refreshRules() {
        rulesPending = true; rulesRequested = true;
        long epoch = ++rulesEpoch;
        api.rules().whenComplete((next, error) -> Minecraft.getInstance().execute(() -> {
            if (epoch != rulesEpoch) return;
            rulesPending = false;
            if (error == null) {
                rules = next; invalidate();
                if (Minecraft.getInstance().screen instanceof RatingScreen screen) screen.refreshTranslations();
            }
        }));
    }

    public RatingRules rules() { return rules; }
    public int nameplateDistance() { return Math.min(config.nameplateDistance, rules.maxNameplateDistance); }

    private String preferredLanguage() { return config.language.equals("auto") ? Minecraft.getInstance().options.languageCode : config.language; }

    private void refreshLanguage() {
        String preferred = preferredLanguage();
        languagePending = true;
        long current = ++languageEpoch;
        api.translations(preferred).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
            if (current != languageEpoch) return;
            languagePending = false;
            if (error != null) { untilLanguage = 100; return; }
            if (!preferred.equals(preferredLanguage())) return;
            requestedLanguage = preferred;
            language.apply(result);
            if (Minecraft.getInstance().screen instanceof RatingScreen screen) screen.refreshTranslations();
        }));
    }

    private void refreshSummaries() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        List<UUID> targets = mc.level.players().stream()
                .filter(player -> player.distanceToSqr(mc.player) <= nameplateDistance() * nameplateDistance())
                .sorted(Comparator.comparingDouble(player -> player.distanceToSqr(mc.player)))
                .limit(64).map(Player::getUUID).toList();
        if (targets.isEmpty()) return;
        refreshing = true;
        untilRefresh = rules.summaryRefreshTicks;
        long current = generation;
        api.summaries(self().id(), targets).whenComplete((data, error) -> mc.execute(() -> {
            if (generation != current) return;
            refreshing = false;
            summaries = error == null ? Map.copyOf(data) : Map.of();
        }));
    }

    public void invalidate() {
        generation++;
        refreshing = false;
        untilRefresh = 0;
        summaries = Map.of();
    }

    public void configChanged() {
        requestedLanguage = ""; untilLanguage = 0;
        invalidate();
    }

    public void applyConfig(RatingConfig next) { config = next; configChanged(); }

    public RatingConfig config() { return config; }
    public RatingApi api() { return api; }
    public Summary summary(UUID player) { return summaries.get(player.toString()); }

    public Identity self() {
        var mc = Minecraft.getInstance();
        return mc.player != null ? identity(mc.player) : new Identity(mc.getUser().getProfileId(), mc.getUser().getName());
    }

    public static Identity identity(Player player) { return new Identity(player.getUUID(), player.getGameProfile().getName()); }

    public List<Identity> nearby() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return List.of();
        return mc.level.players().stream().filter(player -> player != mc.player && !player.isSpectator()
                        && !player.isInvisibleTo(mc.player) && player.distanceToSqr(mc.player) <= rules.ratingDistance * rules.ratingDistance
                        && (!rules.requireLineOfSight || mc.player.hasLineOfSight(player)))
                .sorted(Comparator.comparingDouble(player -> player.distanceToSqr(mc.player)))
                .map(SocialRatingClient::identity).toList();
    }

    public boolean canRate(UUID target) { return nearby().stream().anyMatch(player -> player.id().equals(target)); }

    public Player aimedPlayer() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        Vec3 start = mc.player.getEyePosition();
        Vec3 end = start.add(mc.player.getViewVector(1).scale(rules.ratingDistance));
        if (rules.requireLineOfSight) end = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player)).getLocation();
        Player selected = null;
        double nearest = rules.ratingDistance * rules.ratingDistance;
        for (var player : mc.level.players()) {
            if (player == mc.player || player.isSpectator() || player.isInvisibleTo(mc.player)) continue;
            var hit = player.getBoundingBox().inflate(0.1).clip(start, end);
            if (hit.isPresent() && start.distanceToSqr(hit.get()) < nearest) {
                nearest = start.distanceToSqr(hit.get());
                selected = player;
            }
        }
        return selected;
    }

    public void openProfile(Screen parent, Identity target) {
        Minecraft.getInstance().setScreen(new ProfileScreen(parent, this, target));
    }

    public void openNearby(Screen parent) {
        Minecraft.getInstance().setScreen(new NearbyScreen(parent, this));
    }

    public Component nameplate(UUID target) {
        if (!rules.nameplatesEnabled) return null;
        Summary summary = summary(target);
        if (summary == null) return null;
        return Component.literal(nameplateText(summary.average(), config.nameplateMode)).withColor(summary.count() == 0 ? 0xb6c2d1 : 0xffd575);
    }

    public static String nameplateText(Double value, String mode) {
        if (value == null) return "☆";
        double score = Math.clamp(value, 1, MAX_SCORE);
        if ("stars".equals(mode)) return "★".repeat((int) Math.round(score));
        return "★ " + average(score).replaceFirst("\\.0$", "") + "/" + MAX_SCORE;
    }

    public static String average(Double value) { return value == null ? "—" : String.format(Locale.ROOT, "%.1f", value); }
    public static String stars(Integer value) { return value == null ? "—" : "★".repeat(value) + "☆".repeat(MAX_SCORE - value); }
    public static net.minecraft.network.chat.MutableComponent tr(String key, Object... values) {
        return Component.translatable("socialrating." + key, values);
    }
}
