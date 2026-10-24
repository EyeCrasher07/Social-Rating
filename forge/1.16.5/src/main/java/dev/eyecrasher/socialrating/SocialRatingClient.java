package dev.eyecrasher.socialrating;

import net.minecraft.client.util.InputMappings;
import dev.eyecrasher.socialrating.ui.NearbyScreen;
import dev.eyecrasher.socialrating.ui.ProfileScreen;
import dev.eyecrasher.socialrating.ui.RatingScreen;
import dev.eyecrasher.socialrating.ui.AdminReportsScreen;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RayTraceContext;
import net.minecraft.util.math.vector.Vector3d;

import static dev.eyecrasher.socialrating.RatingData.*;

public final class SocialRatingClient {
    public static SocialRatingClient INSTANCE;
    private RatingConfig config;
    private RatingApi api;
    private KeyBinding profileKey;
    private volatile Map<String, Summary> summaries = dev.eyecrasher.socialrating.JavaCompat.map();
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
            var signal = new com.google.gson.JsonParser().parse(packet.json()).getAsJsonObject();
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
        profileKey = RatingClientPlatform.key(new KeyBinding("key.socialrating.profile", 82, category));
        RatingClientPlatform.tick(this::tick);
    }

    public void renderHint(dev.eyecrasher.socialrating.ui.RatingGraphics graphics) {
        var mc = Minecraft.getInstance();
        if (!config.showInteractionHint || !rules.interactionHintEnabled || mc.screen != null) return;
        PlayerEntity target = aimedPlayer();
        if (target != null) graphics.drawCenteredString(mc.font, tr("hint", profileKey.getTranslatedKeyMessage(), target.getGameProfile().getName()),
                graphics.guiWidth() / 2, graphics.guiHeight() / 2 + 18, 0xffffd575);
    }

    private void tick(Minecraft mc) {
        api.tick();
        language.tick();
        if (world != mc.level) {
            world = mc.level;
            generation++;
            summaries = dev.eyecrasher.socialrating.JavaCompat.map();
            refreshing = false;
            untilRefresh = 0;
        }
        boolean clicked = false;
        while (profileKey.consumeClick()) clicked = true;
        if ((clicked || openRequested) && mc.player != null && mc.screen == null) {
            openRequested = false;
            PlayerEntity target = aimedPlayer();
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
                .limit(64).map(PlayerEntity::getUUID).collect(java.util.stream.Collectors.toList());
        if (targets.isEmpty()) return;
        refreshing = true;
        untilRefresh = rules.summaryRefreshTicks;
        long current = generation;
        api.summaries(self().id(), targets).whenComplete((data, error) -> mc.execute(() -> {
            if (generation != current) return;
            refreshing = false;
            summaries = error == null ? dev.eyecrasher.socialrating.JavaCompat.copyMap(data) : dev.eyecrasher.socialrating.JavaCompat.map();
        }));
    }

    public void invalidate() {
        generation++;
        refreshing = false;
        untilRefresh = 0;
        summaries = dev.eyecrasher.socialrating.JavaCompat.map();
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
        return mc.player != null ? identity(mc.player) : new Identity(mc.getUser().getGameProfile().getId(), mc.getUser().getName());
    }

    public static Identity identity(PlayerEntity player) { return new Identity(player.getUUID(), player.getGameProfile().getName()); }

    public List<Identity> nearby() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return dev.eyecrasher.socialrating.JavaCompat.list();
        return mc.level.players().stream().filter(player -> player != mc.player && !player.isSpectator()
                        && !player.isInvisibleTo(mc.player) && player.distanceToSqr(mc.player) <= rules.ratingDistance * rules.ratingDistance
                        && (!rules.requireLineOfSight || mc.player.canSee(player)))
                .sorted(Comparator.comparingDouble(player -> player.distanceToSqr(mc.player)))
                .map(SocialRatingClient::identity).collect(java.util.stream.Collectors.toList());
    }

    public boolean canRate(UUID target) { return nearby().stream().anyMatch(player -> player.id().equals(target)); }

    public PlayerEntity aimedPlayer() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        Vector3d start = mc.player.getEyePosition(1.0f);
        Vector3d end = start.add(mc.player.getViewVector(1).scale(rules.ratingDistance));
        if (rules.requireLineOfSight) end = mc.level.clip(new RayTraceContext(start, end, RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE, mc.player)).getLocation();
        PlayerEntity selected = null;
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

    public ITextComponent nameplate(UUID target) {
        if (!rules.nameplatesEnabled) return null;
        Summary summary = summary(target);
        if (summary == null) return null;
        return new net.minecraft.util.text.StringTextComponent(nameplateText(summary.average(), config.nameplateMode)).withStyle(style -> style.withColor(net.minecraft.util.text.Color.fromRgb(summary.count() == 0 ? 0xb6c2d1 : 0xffd575)));
    }

    public static String nameplateText(Double value, String mode) {
        if (value == null) return "☆";
        double score = dev.eyecrasher.socialrating.JavaCompat.clamp(value, 1, MAX_SCORE);
        if ("stars".equals(mode)) return dev.eyecrasher.socialrating.JavaCompat.repeat("★", (int) Math.round(score));
        return "★ " + average(score).replaceFirst("\\.0$", "") + "/" + MAX_SCORE;
    }

    public static String average(Double value) { return value == null ? "—" : String.format(Locale.ROOT, "%.1f", value); }
    public static String stars(Integer value) { return value == null ? "—" : dev.eyecrasher.socialrating.JavaCompat.repeat("★", value) + dev.eyecrasher.socialrating.JavaCompat.repeat("☆", MAX_SCORE - value); }
    public static net.minecraft.util.text.IFormattableTextComponent tr(String key, Object... values) {
        return new net.minecraft.util.text.TranslationTextComponent("socialrating." + key, values);
    }
}
