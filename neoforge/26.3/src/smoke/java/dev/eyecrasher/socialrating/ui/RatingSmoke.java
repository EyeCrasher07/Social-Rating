package dev.eyecrasher.socialrating.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.authlib.GameProfile;
import dev.eyecrasher.socialrating.SocialRatingClient;
import dev.eyecrasher.socialrating.RatingPlatform;
import dev.eyecrasher.socialrating.RatingException;
import dev.eyecrasher.socialrating.RatingStore;
import dev.eyecrasher.socialrating.RatingConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import static dev.eyecrasher.socialrating.RatingData.*;
import static dev.eyecrasher.socialrating.SocialRatingClient.tr;

/** Two real clients: one opens its integrated world to LAN, the other joins it. */
@net.neoforged.fml.common.Mod(value="socialrating_smoke", dist=net.neoforged.api.distmarker.Dist.CLIENT)
public final class RatingSmoke {
    private static final String COMMENT = "Помог построить ферму. Отличный напарник!";
    private static final String REPOST = "Новый отзыв после удаления предыдущего.";
    private static final String HOST_HINT = "Комментарий сервера";
    private final Path shared = Path.of(System.getProperty("socialrating.smoke.shared"));
    private final boolean guest = Boolean.getBoolean("socialrating.smoke.guest");
    private Identity target;
    private SocialRatingClient client;
    private int phase, ticks;
    private boolean waiting;
    private long started;
    private String removedReview;
    private Profile listFixture;
    private final List<Integer> nearbyFixtureIds = new java.util.ArrayList<>();

    public RatingSmoke() {
        started = System.nanoTime();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    private void advance(int next) { phase = next; ticks = 0; }
    private <T> void await(CompletableFuture<T> future, Consumer<T> action) {
        waiting = true;
        future.whenComplete((value, error) -> Minecraft.getInstance().execute(() -> {
            waiting = false;
            if (error != null) throw new AssertionError("Smoke phase " + phase, error);
            action.accept(value);
        }));
    }

    private void tick(Minecraft mc) {
        if (System.nanoTime() - started > 240_000_000_000L) throw new AssertionError("Smoke timeout, phase " + phase);
        if (waiting || mc.gui.overlay() != null) return;
        client = SocialRatingClient.INSTANCE;
        ticks++;
        if (guest) {
            if (phase == 0 && mc.player == null && ticks == 100 && !(mc.gui.screen() instanceof net.minecraft.client.gui.screens.ConnectScreen)) {
                String address = "127.0.0.1:" + Integer.getInteger("socialrating.smoke.port");
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.gui.screen(), mc,
                        net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                        new net.minecraft.client.multiplayer.ServerData("Social Rating test", address, net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
            }
            guestTick(mc); return;
        }
        switch (phase) {
            case 0 -> {
                if (ticks < 50) return;
                mc.options.pauseOnLostFocus = false;
                mc.options.guiScale().set(2);
                var settings = new LevelSettings("Social Rating host test", GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("rating-host-" + System.currentTimeMillis(), settings,
                        new WorldOptions(4207, false, false), WorldPresets::createTestWorldDimensions, mc.gui.screen());
                advance(1);
            }
            case 1 -> {
                if (mc.player == null || ticks < 60) return;
                var server = mc.getSingleplayerServer();
                int port = Integer.getInteger("socialrating.smoke.port");
                require(server.publishServer(MinecraftServer.MultiplayerScope.LAN, false, port), "LAN host must open");
                server.setUsesAuthentication(false); // Development profiles only, in this disposable test world.
                write("host-ready.txt", Integer.toString(port));
                mc.gui.setScreen(null);
                advance(2);
            }
            case 2 -> {
                if (ticks % 20 != 0) return;
                var self = client.self();
                var ready = new CompletableFuture<Identity>();
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var friend = server.getPlayerList().getPlayerByName("RatingFriend");
                    var host = server.getPlayerList().getPlayer(self.id());
                    if (friend == null) { ready.complete(null); return; }
                    friend.teleportTo(host.getX(), host.getY(), host.getZ() + 3);
                    friend.setYRot(180);
                    var identity = new Identity(friend.getUUID(), friend.getGameProfile().name());
                    var directory = RatingPlatform.configDirectory().resolve("socialrating");
                    var store = new RatingStore(directory.resolve("database.json"));
                    for (int i = 1; i <= 3; i++) {
                        var author = new Identity(new UUID(0, 100 + i), "Builder" + i);
                        store.save(i == 2 ? identity : author, self, new Integer[] {3, 3, 3}, "Помог разобраться с фермой. Приятно играть вместе. ".repeat(4));
                        store.save(author, identity, new Integer[] {3, 3, 3}, "Приятный напарник.");
                    }
                    try {
                        var json = new Gson().fromJson(Files.readString(directory.resolve("lang/ru_ru.json")), JsonObject.class);
                        json.addProperty("socialrating.comment_optional", HOST_HINT);
                        json.addProperty("key.socialrating.profile", "Оценить на сервере");
                        Files.writeString(directory.resolve("lang/ru_ru.json"), json.toString());
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "socialrating admin reload");
                    } catch (Exception error) { ready.completeExceptionally(error); return; }
                    ready.complete(identity);
                });
                await(ready, identity -> { if (identity != null) { target = identity; client.invalidate(); advance(3); } });
            }
            case 3 -> {
                if (ticks < 100 || client.summary(target.id()) == null) return;
                require(tr("comment_optional").getString().equals(HOST_HINT), "Host edits must reload into the host client");
                require(componentText("key.socialrating.profile").equals("Оценить на сервере"), "Native keybinding translations must be overridden too");
                mc.player.setYRot(0); mc.player.setXRot(0);
                require(client.canRate(target.id()), "Real LAN guest must be nearby");
                var commandRoot = mc.player.connection.getCommands().getRoot();
                var ratingCommand = commandRoot.getChild("socialrating");
                require(ratingCommand != null && ratingCommand.getChild("admin") != null && ratingCommand.getChild("reload") == null
                                && commandRoot.getChild("ratingadmin") == null,
                        "Operator command suggestions must offer socialrating admin instead of a root reload or ratingadmin command");
                require(client.aimedPlayer() != null && client.aimedPlayer().getUUID().equals(target.id()), "Targeting must find real guest");
                require(renderState(mc).scoreText.getString().equals("★ 3/5"), "Numeric nameplate must contain only a star and the five-point average");
                shot(mc, "01-nameplate");
                client.config().nameplateMode = "stars";
                require(renderState(mc).scoreText.getString().equals("★★★"), "Stars mode must contain exactly three stars and no extra text");
                require(SocialRatingClient.nameplateText(3.2, "stars").equals("★★★")
                        && SocialRatingClient.nameplateText(3.8, "stars").equals("★★★★")
                        && SocialRatingClient.nameplateText(5.0, "stars").equals("★★★★★")
                        && SocialRatingClient.nameplateText(null, "stars").equals("☆"), "Stars display must handle averages and empty profiles");
                advance(32);
            }
            case 32 -> {
                if (ticks < 20) return;
                shot(mc, "01-nameplate-stars");
                client.config().nameplateMode = "number";
                client.openNearby(null); advance(50);
            }
            case 50 -> {
                if (ticks < 20) return;
                requireFixedNearbyCard(mc, 1);
                shot(mc, "29-nearby-single");
                click(mc.gui.screen(), "nearby.profile"); advance(51);
            }
            case 51 -> {
                if (ticks < 40) return;
                require(mc.gui.screen() instanceof ProfileScreen && mc.gui.screen().getTitle().getString().contains(target.name()),
                        "The nearby row must open the real LAN player's profile");
                mc.gui.screen().onClose();
                require(mc.gui.screen() instanceof NearbyScreen, "Closing the profile must return to nearby players");
                // Extra client entities only populate this disposable visual fixture; they are never packaged.
                String[] names = {"Builder_07", "RedstoneFox", "Miner_42", "AlexCraft", "StoneKeeper"};
                double[] distances = {1.5, 2.3, 3.6, 4.5, 5.3};
                for (int i = 0; i < names.length; i++) {
                    var player = new RemotePlayer(mc.level, new GameProfile(new UUID(8, i + 1), names[i]));
                    player.setId(-7000 - i);
                    player.setPos(mc.player.getX() + distances[i], mc.player.getY(), mc.player.getZ());
                    mc.level.addEntity(player);
                    nearbyFixtureIds.add(player.getId());
                }
                client.openNearby(null); advance(52);
            }
            case 52 -> {
                if (ticks < 20) return;
                require(client.nearby().size() == 6, "Nearby fixture must contain the real friend and five extra players");
                requireFixedNearbyCard(mc, 4);
                shot(mc, "30-nearby-full");
                mc.options.guiScale().set(3); advance(53);
            }
            case 53 -> {
                if (ticks < 20) return;
                requireFixedNearbyCard(mc, 4);
                shot(mc, "31-nearby-compact");
                click(mc.gui.screen(), "next"); advance(54);
            }
            case 54 -> {
                if (ticks < 20) return;
                requireFixedNearbyCard(mc, 2);
                shot(mc, "32-nearby-next");
                for (int id : nearbyFixtureIds) mc.level.removeEntity(id, Entity.RemovalReason.DISCARDED);
                nearbyFixtureIds.clear();
                click(mc.gui.screen(), "refresh");
                requireFixedNearbyCard(mc, 1);
                require(buttons(mc.gui.screen()).stream().filter(button -> button.getMessage().getString().equals(tr("previous").getString())
                                || button.getMessage().getString().equals(tr("next").getString())).noneMatch(button -> button.active),
                        "Refreshing a shortened list must return to its available page and disable navigation");
                moveFriend(mc, 30, 55);
            }
            case 55 -> {
                if (ticks < 40) return;
                require(client.nearby().isEmpty(), "A distant player must no longer appear in nearby results");
                click(mc.gui.screen(), "refresh"); advance(56);
            }
            case 56 -> {
                if (ticks < 20) return;
                requireFixedNearbyCard(mc, 0);
                shot(mc, "33-nearby-empty");
                moveFriend(mc, 3, 57);
            }
            case 57 -> {
                if (ticks < 40 || !client.canRate(target.id())) return;
                mc.options.guiScale().set(2);
                client.openProfile(null, target); advance(4);
            }
            case 4 -> {
                if (ticks < 40) return;
                var card = (ProfileScreen) mc.gui.screen();
                require(card.panelWidth <= 280 && card.bottom - card.top <= 208, "Profile must be compact");
                shot(mc, "02-profile");
                var stars = buttons(mc.gui.screen()).stream().filter(button -> button.getMessage().getString().equals("☆")).toList();
                require(stars.size() == 15, "Five inline stars per category");
                press(stars.get(4)); press(stars.get(8)); press(stars.get(14));
                edit(mc.gui.screen()).setValue(COMMENT); advance(5);
            }
            case 5 -> {
                if (ticks < 20) return;
                shot(mc, "03-rate");
                mc.options.guiScale().set(3);
                require(edit(mc.gui.screen()).getValue().equals(COMMENT), "Resize must preserve draft");
                mc.options.guiScale().set(2);
                click(mc.gui.screen(), "save_short"); advance(6);
            }
            case 6 -> {
                if (ticks < 50) return;
                await(client.api().profile(client.self().id(), target.id()), profile -> {
                    require(profile.summary().count() == 4 && profile.myReview().skill() == 4 && profile.myReview().ingenuity() == 5,
                            "Server must persist widget-selected four- and five-star scores");
                    require(profile.myReview().author().equals(client.self().id().toString()), "Server must use connection identity");
                    mc.gui.setScreen(null);
                    mc.player.setYRot(90);
                    mc.player.connection.sendCommand("socialrating"); advance(7);
                });
            }
            case 7 -> {
                if (ticks < 40) return;
                require(mc.gui.screen() instanceof ProfileScreen, "The socialrating server command must open the profile");
                shot(mc, "04-own-profile");
                await(client.api().profile(client.self().id(), client.self().id()), profile -> {
                    require(profile.sentCount() == 1, "Host should see submitted review");
                    listFixture = profile;
                    var many = new java.util.ArrayList<>(profile.reviews());
                    var original = profile.reviews().getFirst();
                    for (int i = 1; i <= 4; i++) many.add(new Review(new UUID(5, i).toString(), new UUID(6, i).toString(), profile.id(),
                            "Reviewer" + i, profile.name(), 5, null, i, "Visual fixture comment", original.updatedAt(), 1, ""));
                    var preview = new Profile(profile.id(), profile.name(), profile.summary(), many, profile.sent(), profile.myReview(), many.size(), profile.sentCount());
                    mc.gui.setScreen(new ReviewListScreen((ProfileScreen) mc.gui.screen(), client, client.self(), preview, false)); advance(8);
                });
            }
            case 8 -> {
                if (ticks < 20) return;
                requireFixedReviewCard(mc);
                require(buttons(mc.gui.screen()).stream().filter(button -> button.getMessage().getString().equals(tr("review.open").getString())).count() == 4,
                        "The fixed review list must fit four compact entries per page");
                shot(mc, "05-reviews"); click(mc.gui.screen(), "next"); advance(46);
            }
            case 46 -> {
                if (ticks < 20) return;
                require(buttons(mc.gui.screen()).stream().filter(button -> button.getMessage().getString().equals(tr("review.open").getString())).count() == 3,
                        "The next page must show the remaining compact entries");
                shot(mc, "25-review-browser-next");
                var original = listFixture.reviews().getFirst();
                var single = new Review(original.id(), original.author(), original.target(), original.authorName(), original.targetName(),
                        5, null, 1, "Хороший напарник.", original.updatedAt(), original.revision(), original.reportStatus());
                var profile = new Profile(listFixture.id(), listFixture.name(), listFixture.summary(), List.of(single), List.of(), null, 1, 0);
                var parent = (ProfileScreen) ((RatingScreen) mc.gui.screen()).parent;
                mc.gui.setScreen(new ReviewListScreen(parent, client, client.self(), profile, false)); advance(47);
            }
            case 47 -> {
                if (ticks < 20) return;
                requireFixedReviewCard(mc);
                require(buttons(mc.gui.screen()).stream().filter(button -> button.getMessage().getString().equals(tr("next").getString())
                                || button.getMessage().getString().equals(tr("previous").getString())).noneMatch(button -> button.active),
                        "Single-review navigation must retain its layout while disabling unavailable directions");
                shot(mc, "26-review-browser-single");
                var profile = new Profile(listFixture.id(), listFixture.name(), listFixture.summary(), List.of(), List.of(), null, 0, 0);
                var parent = (ProfileScreen) ((RatingScreen) mc.gui.screen()).parent;
                mc.gui.setScreen(new ReviewListScreen(parent, client, client.self(), profile, false)); advance(48);
            }
            case 48 -> {
                if (ticks < 20) return;
                requireFixedReviewCard(mc);
                require(buttons(mc.gui.screen()).stream().noneMatch(button -> button.getMessage().getString().equals(tr("review.open").getString())),
                        "The empty review list must not offer a review action");
                shot(mc, "27-review-browser-empty");
                var parent = (ProfileScreen) ((RatingScreen) mc.gui.screen()).parent;
                mc.options.guiScale().set(3);
                mc.gui.setScreen(new ReviewListScreen(parent, client, client.self(), listFixture, false)); advance(49);
            }
            case 49 -> {
                if (ticks < 20) return;
                requireFixedReviewCard(mc);
                shot(mc, "28-review-browser-compact");
                mc.options.guiScale().set(2); click(mc.gui.screen(), "review.open"); advance(9);
            }
            case 9 -> { if (ticks >= 20) { shot(mc, "06-review"); click(mc.gui.screen(), "field.comment_tab"); advance(33); } }
            case 33 -> { if (ticks >= 20) { shot(mc, "06-review-comment"); click(mc.gui.screen(), "report"); advance(10); } }
            case 10 -> {
                if (ticks < 20) return;
                edit(mc.gui.screen()).setValue("Проверка жалобы у хоста мира."); advance(11);
            }
            case 11 -> { if (ticks >= 20) { shot(mc, "07-report"); click(mc.gui.screen(), "send_report"); advance(12); } }
            case 12 -> {
                if (ticks < 40) return;
                await(client.api().profile(client.self().id(), client.self().id()), profile -> {
                    require(profile.reviews().stream().anyMatch(review -> review.reportStatus().equals("pending")), "Report must be stored by host");
                    mc.gui.setScreen(null);
                    mc.player.connection.sendCommand("socialrating admin"); advance(21);
                });
            }
            case 21 -> {
                if (ticks < 40) return;
                require(mc.gui.screen() instanceof AdminReportsScreen, "The operator command must open the report menu over Minecraft networking");
                shot(mc, "13-admin-pending"); click(mc.gui.screen(), "admin.open"); advance(22);
            }
            case 22 -> {
                if (ticks < 30) return;
                shot(mc, "14-admin-data"); click(mc.gui.screen(), "admin.complaint_tab"); advance(34);
            }
            case 34 -> { if (ticks >= 20) { shot(mc, "14-admin-complaint"); click(mc.gui.screen(), "admin.review_tab"); advance(23); } }
            case 23 -> {
                if (ticks < 20) return;
                shot(mc, "15-admin-evidence");
                mc.options.guiScale().set(3); advance(35);
            }
            case 35 -> {
                if (ticks < 20) return;
                shot(mc, "18-compact-admin-evidence");
                var card = (AdminReportScreen) mc.gui.screen();
                require(card.panelWidth == 304 && card.bottom - card.top == 224, "Admin detail must adapt to a 320x240 GUI without losing fields");
                var next = buttons(mc.gui.screen()).stream().filter(button -> button.getMessage().getString().equals(tr("next").getString())).findFirst().orElseThrow();
                require(next.active, "Long evidence must be paginated without truncation");
                press(next); click(mc.gui.screen(), "field.data_tab"); advance(36);
            }
            case 36 -> { if (ticks >= 20) { shot(mc, "19-compact-admin-data"); click(mc.gui.screen(), "next"); advance(37); } }
            case 37 -> {
                if (ticks < 20) return;
                shot(mc, "20-compact-admin-times");
                mc.options.guiScale().set(2);
                click(mc.gui.screen(), "admin.review_tab"); click(mc.gui.screen(), "admin.keep_review");
                edit(mc.gui.screen()).setValue("Проверено: отзыв без нарушений."); advance(41);
            }
            case 41 -> { if (ticks >= 20) { shot(mc, "24-decision-reason"); click(mc.gui.screen(), "confirm"); advance(24); } }
            case 24 -> {
                if (ticks < 40) return;
                require(mc.gui.screen() instanceof AdminReportsScreen, "Resolving must return to the refreshed list");
                await(client.api().reports(true, 0), reports -> {
                    require(reports.pending() == 0 && reports.resolved() == 1, "Keep must archive the report");
                    click(mc.gui.screen(), "admin.archive", 1); advance(25);
                });
            }
            case 25 -> { if (ticks >= 30) { shot(mc, "16-admin-archive"); click(mc.gui.screen(), "admin.open"); advance(26); } }
            case 26 -> {
                if (ticks < 30) return;
                require(buttons(mc.gui.screen()).stream().noneMatch(button -> button.getMessage().getString().equals(tr("admin.remove_review").getString())),
                        "Archived reports must not offer a second decision");
                require(buttons(mc.gui.screen()).stream().noneMatch(button -> button.getMessage().getString().equals(tr("next").getString())),
                        "An archived report must not add a second metadata page just for its moderator");
                require(hasReviewer(mc.gui.screen()), "The moderator must be visible on the first metadata page");
                shot(mc, "17-admin-resolved");
                mc.options.guiScale().set(3); advance(38);
            }
            case 38 -> {
                if (ticks < 20) return;
                require(hasReviewer(mc.gui.screen()), "The moderator must remain visible in a compact GUI");
                shot(mc, "22-compact-admin-decision");
                click(mc.gui.screen(), "next"); advance(40);
            }
            case 40 -> {
                if (ticks < 20) return;
                require(hasReviewer(mc.gui.screen()), "Metadata pagination must not hide the moderator footer");
                shot(mc, "23-compact-admin-decision-times");
                mc.options.guiScale().set(2);
                click(mc.gui.screen(), "decision.tab"); advance(42);
            }
            case 42 -> {
                if (ticks < 20) return;
                shot(mc, "21-admin-decision");
                await(client.api().profile(client.self().id(), client.self().id()), profile -> {
                    require(profile.summary().count() == 3, "Keeping a review must preserve reputation");
                    var review = profile.reviews().stream().filter(value -> value.author().equals(target.id().toString()) && value.reportStatus().isEmpty()).findFirst().orElseThrow();
                    removedReview = review.id();
                    await(client.api().report(client.self().id(), review.id(), "abuse", "Проверка удаления оператором."), result -> {
                        mc.gui.screen().onClose(); advance(27);
                    });
                });
            }
            case 27 -> { if (ticks >= 30) { click(mc.gui.screen(), "admin.waiting", 1); advance(28); } }
            case 28 -> { if (ticks >= 30) { click(mc.gui.screen(), "admin.open"); advance(29); } }
            case 29 -> { if (ticks >= 30) { click(mc.gui.screen(), "admin.remove_review"); edit(mc.gui.screen()).setValue("Оскорбление в отзыве."); click(mc.gui.screen(), "confirm"); advance(30); } }
            case 30 -> {
                if (ticks < 40) return;
                await(client.api().reports(true, 0), reports -> {
                    require(reports.pending() == 0 && reports.resolved() == 2, "Removal must archive the second report");
                    await(client.api().profile(client.self().id(), client.self().id()), profile -> {
                        require(profile.summary().count() == 2, "Removed review must disappear from reputation");
                        write("guest-repost-ready.txt", removedReview); advance(39);
                    });
                });
            }
            case 39 -> {
                if (!Files.exists(shared.resolve("guest-repost-saved.txt"))) return;
                await(client.api().profile(client.self().id(), client.self().id()), profile -> {
                    var review = profile.reviews().stream().filter(value -> value.author().equals(target.id().toString())).findFirst().orElseThrow();
                    require(profile.summary().count() == 3 && review.comment().equals(REPOST) && !review.id().equals(removedReview),
                            "A new review from the same author must reach the recipient and count independently of the removed one");
                    await(client.api().reports(false, 0), reports -> {
                        require(reports.resolved() == 2 && reports.pending() == 0
                                        && reports.reports().stream().anyMatch(report -> report.status().equals("removed") && report.snapshot().id().equals(removedReview)),
                                "The removed review's report must remain archived after a new submission");
                        require(reports.reports().stream().anyMatch(report -> report.decisionReason().equals("Оскорбление в отзыве.")), "Moderation reason must persist separately");
                        write("guest-withdraw-ready.txt", "withdraw"); advance(43);
                    });
                });
            }
            case 43 -> {
                if (!Files.exists(shared.resolve("guest-withdraw-saved.txt"))) return;
                await(client.api().profile(client.self().id(), client.self().id()), profile -> {
                    require(profile.summary().count() == 2 && profile.reviews().stream().noneMatch(review -> review.author().equals(target.id().toString())),
                            "Author withdrawal must remove the review from recipient reputation");
                    mc.gui.setScreen(new SettingsScreen(mc.gui.screen(), client)); advance(13);
                });
            }
            case 13 -> {
                if (ticks < 20) return;
                shot(mc, "08-settings");
                click(mc.gui.screen(), "nameplate_mode", tr("nameplate_mode.number"));
                click(mc.gui.screen(), "save_settings");
                require(RatingConfig.load().nameplateMode.equals("stars"), "Display mode must be configurable and persist across reload");
                client.openProfile(null, new Identity(new UUID(0, 999), "NewPlayer")); advance(14);
            }
            case 14 -> {
                if (ticks < 30) return;
                shot(mc, "09-empty-profile"); mc.options.guiScale().set(3);
                client.openProfile(null, client.self()); advance(15);
            }
            case 15 -> { if (ticks >= 30) { shot(mc, "10-compact-profile"); client.openProfile(null, target); advance(16); } }
            case 16 -> {
                if (ticks < 30) return;
                shot(mc, "11-compact-rate");
                var server = mc.getSingleplayerServer();
                var moved = new CompletableFuture<Void>();
                server.execute(() -> {
                    var host = server.getPlayerList().getPlayer(client.self().id());
                    server.getPlayerList().getPlayer(target.id()).teleportTo(host.getX() + 50, host.getY(), host.getZ());
                    moved.complete(null);
                });
                await(moved, unused -> advance(17));
            }
            case 17 -> {
                if (ticks < 30) return;
                var rejected = client.api().save(client.self(), target, new Integer[] {1, 1, 1}, "Must be rejected").handle((value, error) -> {
                    require(error != null && RatingException.errorCode(error).equals("not_nearby"), "Server must independently reject distant ratings"); return true;
                });
                await(rejected, unused -> advance(18));
            }
            case 18 -> {
                if (!Files.exists(shared.resolve("guest-verified.txt"))) return;
                var server = mc.getSingleplayerServer();
                Path database = RatingPlatform.configDirectory().resolve("socialrating/database.json");
                require(Files.exists(database), "Database must be in the host mod configuration directory");
                require(!Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("data/socialrating.json")), "No new database may be written into the world");
                var persisted = new RatingStore(database).profile(client.self().id(), target.id());
                require(persisted.myReview().comment().equals(COMMENT) && persisted.myReview().skill() == 4, "Server restart/reopen must retain the accepted review");
                var loaded = new CompletableFuture<Void>();
                server.execute(() -> {
                    try {
                        Path languages = database.getParent().resolve("lang");
                        var custom = new Gson().fromJson(Files.readString(languages.resolve("ru_ru.json")), JsonObject.class);
                        custom.addProperty("socialrating.saved", "Мой перевод");
                        Files.writeString(languages.resolve("custom_test.json"), custom.toString());
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "socialrating admin reload");
                        loaded.complete(null);
                    } catch (Exception error) { loaded.completeExceptionally(error); }
                });
                await(loaded, unused -> {
                    var config = RatingConfig.load();
                    config.language = "custom_test";
                    try { config.save(); } catch (Exception error) { throw new AssertionError(error); }
                    mc.gui.setScreen(null);
                    mc.player.setYRot(90);
                    mc.player.connection.sendCommand("socialrating"); advance(19);
                });
            }
            case 19 -> {
                if (ticks < 50) return;
                require(tr("saved").getString().equals("Мой перевод"), "A newly added custom language must reach the client");
                require(client.config().language.equals("custom_test") && mc.gui.screen() instanceof ProfileScreen,
                        "The socialrating command must pick up edited client settings when opening the profile");
                shot(mc, "12-custom-language");
                var server = mc.getSingleplayerServer();
                var reloaded = new CompletableFuture<Void>();
                server.execute(() -> {
                    try {
                        Path settings = RatingPlatform.configDirectory().resolve("socialrating/server.json");
                        var json = new Gson().fromJson(Files.readString(settings), JsonObject.class);
                        json.addProperty("ratingsEnabled", false); json.addProperty("commentsEnabled", false);
                        json.addProperty("skillEnabled", false); json.addProperty("ratingDistance", 64);
                        json.addProperty("requireLineOfSight", false); json.addProperty("maxCommentLength", 48);
                        json.addProperty("nameplatesEnabled", false); json.addProperty("interactionHintEnabled", false);
                        json.addProperty("summaryRefreshTicks", 40); json.addProperty("backupRetention", 2);
                        Files.writeString(settings, json.toString());
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "socialrating admin reload");
                        reloaded.complete(null);
                    } catch (Exception error) { reloaded.completeExceptionally(error); }
                });
                await(reloaded, unused -> advance(44));
            }
            case 44 -> {
                if (ticks < 40) return;
                require(!client.rules().ratingsEnabled && !client.rules().commentsEnabled && !client.rules().skillEnabled
                                && client.rules().ratingDistance == 64 && client.rules().maxCommentLength == 48 && client.rules().summaryRefreshTicks == 40,
                        "Reloaded server rules must propagate to clients without reconnecting");
                require(client.nameplate(target.id()) == null, "Server nameplate switch must take effect");
                await(client.api().save(client.self(), target, new Integer[] {5, null, 5}, "").handle((value, error) -> {
                    require(error != null && RatingException.errorCode(error).equals("ratings_disabled"), "Server must enforce reloaded rating rules, including the enlarged distance"); return true;
                }), unused -> {
                    mc.player.connection.sendCommand("socialrating admin backup"); advance(45);
                });
            }
            case 45 -> {
                if (ticks < 40) return;
                Path database = RatingPlatform.configDirectory().resolve("socialrating/database.json");
                try (var snapshots = Files.list(database.getParent().resolve("backups"))) {
                    var backups = snapshots.filter(path -> path.toString().endsWith(".json")).toList();
                    require(!backups.isEmpty() && backups.size() <= 2 && backups.stream().anyMatch(path -> new RatingStore(path).profile(client.self().id(), target.id()).myReview() != null),
                            "Manual/automatic backups must be valid, retained and contain current data");
                } catch (Exception error) { throw new AssertionError(error); }
                write("host-result.txt", "PASS: moderation reasons, participant history, withdrawal, server rule reload and enforcement, backups, cache, ratings and translations\n" + database.toAbsolutePath());
                write("done.txt", "done"); advance(20);
            }
            case 20 -> { if (ticks >= 40) mc.stop(); }
            default -> throw new AssertionError(phase);
        }
    }

    private void guestTick(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        if (Files.exists(shared.resolve("done.txt"))) { mc.stop(); return; }
        if (mc.player == null) return;
        if (phase == 0 && ticks % 40 == 0) await(client.api().profile(client.self().id(), client.self().id()), profile -> {
            if (profile.reviews().stream().anyMatch(review -> review.comment().equals(COMMENT))) {
                mc.gui.setScreen(null);
                mc.player.setYRot(90);
                mc.player.connection.sendCommand("socialrating"); advance(1);
            }
        });
        if (phase == 1 && ticks > 40) {
            var command = mc.player.connection.getCommands().getRoot().getChild("socialrating");
            require(command != null && command.getCommand() != null && command.getChild("admin") == null && command.getChild("reload") == null,
                    "A non-operator may use socialrating, but must not be offered admin or root reload");
            require(mc.gui.screen() instanceof ProfileScreen, "The socialrating command must open a non-operator's profile too");
            require(tr("comment_optional").getString().equals(HOST_HINT), "Guest must receive host-edited translations");
            require(componentText("key.socialrating.profile").equals("Оценить на сервере"), "Guest native translations must come from the host");
            shot(mc, "guest-received");
            var checks = client.api().reports(true, 0).handle((value, error) -> {
                require(error != null && RatingException.errorCode(error).equals("forbidden"), "Non-operators must not read reports"); return true;
            }).thenCompose(unused -> client.api().reportDetails(new UUID(0, 1).toString()).handle((value, error) -> {
                require(error != null && RatingException.errorCode(error).equals("forbidden"), "Non-operators must not read evidence by ID"); return true;
            })).thenCompose(unused -> client.api().resolve(new UUID(0, 1).toString(), true, "Spoofed").handle((value, error) -> {
                require(error != null && RatingException.errorCode(error).equals("forbidden"), "Non-operators must not decide reports"); return true;
            }));
            await(checks, unused -> {
                write("guest-verified.txt", "PASS: guest received host ratings and translations; direct moderator requests rejected without operator rights"); advance(2);
            });
        }
        if (phase == 2 && Files.exists(shared.resolve("guest-repost-ready.txt"))) {
            try { removedReview = Files.readString(shared.resolve("guest-repost-ready.txt")); }
            catch (Exception error) { throw new AssertionError(error); }
            target = client.nearby().getFirst();
            require(!target.id().equals(client.self().id()), "The recipient must be the nearby host");
            client.openProfile(null, target); advance(3);
        }
        if (phase == 3 && ticks >= 40) {
            var stars = buttons(mc.gui.screen()).stream().filter(button -> button.getMessage().getString().equals("☆")).toList();
            require(stars.size() == 15, "A deleted review must leave the rating form available for a fresh submission");
            press(stars.get(4)); press(stars.get(9)); press(stars.get(13));
            edit(mc.gui.screen()).setValue(REPOST);
            click(mc.gui.screen(), "save_short"); advance(4);
        }
        if (phase == 4 && ticks >= 40) {
            await(client.api().profile(client.self().id(), target.id()), profile -> {
                var review = profile.myReview();
                require(review != null && review.comment().equals(REPOST) && !review.id().equals(removedReview)
                                && review.revision() == 1 && review.reportStatus().isEmpty(),
                        "An ordinary player must be able to save a new review after moderator deletion");
                shot(mc, "guest-new-review-after-removal");
                write("guest-repost-saved.txt", review.id()); advance(5);
            });
        }
        if (phase == 5 && Files.exists(shared.resolve("guest-withdraw-ready.txt"))) {
            await(client.api().reports(false, false, 0), reports -> {
                require(reports.reports().size() == 1 && reports.reports().getFirst().decisionReason().equals("Оскорбление в отзыве."),
                        "Review author must see their decision reason, without unrelated reports");
                client.openProfile(null, client.self()); advance(6);
            });
        }
        if (phase == 6 && ticks >= 30) { click(mc.gui.screen(), "my_reports"); advance(7); }
        if (phase == 7 && ticks >= 30) { click(mc.gui.screen(), "admin.archive", 1); advance(8); }
        if (phase == 8 && ticks >= 30) { click(mc.gui.screen(), "admin.open"); advance(9); }
        if (phase == 9 && ticks >= 30) {
            require(buttons(mc.gui.screen()).stream().noneMatch(button -> button.getMessage().getString().equals(tr("admin.remove_review").getString())),
                    "Participant report history must be read-only");
            click(mc.gui.screen(), "decision.tab"); advance(10);
        }
        if (phase == 10 && ticks >= 20) { shot(mc, "guest-decision-reason"); client.openProfile(null, client.self()); advance(11); }
        if (phase == 11 && ticks >= 30) { click(mc.gui.screen(), "sent_short"); advance(12); }
        if (phase == 12 && ticks >= 20) { click(mc.gui.screen(), "review.open"); advance(13); }
        if (phase == 13 && ticks >= 20) { click(mc.gui.screen(), "withdraw"); advance(14); }
        if (phase == 14 && ticks >= 20) { shot(mc, "guest-withdraw-confirm"); click(mc.gui.screen(), "withdraw"); advance(15); }
        if (phase == 15 && ticks >= 30) {
            await(client.api().profile(client.self().id(), target.id()), profile -> {
                require(profile.myReview() == null, "Withdrawal through the normal UI must persist");
                write("guest-withdraw-saved.txt", "withdrawn"); advance(16);
            });
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private EntityRenderState renderState(Minecraft mc) {
        var player = mc.level.players().stream().filter(value -> value.getUUID().equals(target.id())).findFirst().orElseThrow();
        return ((EntityRenderer) mc.getEntityRenderDispatcher().getRenderer(player)).createRenderState(player, 1);
    }
    private void write(String file, String value) {
        try { Files.createDirectories(shared); Files.writeString(shared.resolve(file), value); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private static List<Button> buttons(Screen screen) { return screen.children().stream().filter(value -> value instanceof Button).map(value -> (Button) value).toList(); }
    private static boolean hasReviewer(Screen screen) {
        return screen.children().stream().anyMatch(value -> value instanceof StringWidget label && label.visible
                && label.getMessage().getString().equals(tr("admin.decision", "RatingTester").getString()));
    }
    private static void requireFixedReviewCard(Minecraft mc) {
        var card = (ReviewListScreen) mc.gui.screen();
        require(card.panelWidth == 280 && card.bottom - card.top == 224, "The review browser must keep a fixed size for all review counts and GUI scales");
    }
    private static void requireFixedNearbyCard(Minecraft mc, int rows) {
        var card = (NearbyScreen) mc.gui.screen();
        require(card.panelWidth == 280 && card.bottom - card.top == 224, "Nearby players must keep the same compact size at every count and GUI scale");
        require(buttons(card).stream().filter(button -> button.getMessage().getString().equals(tr("nearby.profile").getString())).count() == rows,
                "Nearby page must expose a profile button for every visible player");
        for (var button : buttons(card)) require(button.getX() >= card.left + 8 && button.getX() + button.getWidth() <= card.left + card.panelWidth - 8
                        && button.getY() >= card.top + 8 && button.getY() + button.getHeight() <= card.bottom - 8,
                "Nearby controls must stay inset within the panel");
    }
    private void moveFriend(Minecraft mc, double distance, int next) {
        var ready = new CompletableFuture<Void>();
        var self = client.self();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            var host = server.getPlayerList().getPlayer(self.id());
            var friend = server.getPlayerList().getPlayer(target.id());
            friend.teleportTo(host.getX(), host.getY(), host.getZ() + distance);
            ready.complete(null);
        });
        await(ready, unused -> advance(next));
    }
    private static EditBox edit(Screen screen) { return screen.children().stream().filter(value -> value instanceof EditBox).map(value -> (EditBox) value).findFirst().orElseThrow(); }
    private static void click(Screen screen, String key, Object... values) { press(buttons(screen).stream().filter(button -> button.getMessage().getString().equals(tr(key, values).getString())).findFirst().orElseThrow()); }
    private static void press(Button button) {
        require(button.active, "Button must be enabled: " + button.getMessage().getString());
        button.onPress(new KeyEvent(InputConstants.KEY_RETURN, InputConstants.KEYCODE_RETURN, 0));
    }
    private static void shot(Minecraft mc, String name) { Screenshot.grab(mc.gameDirectory, name + ".png", mc.gameRenderer.mainRenderTarget(), 1, message -> {}); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static String componentText(String key) { return net.minecraft.network.chat.Component.translatable(key).getString(); }
}
