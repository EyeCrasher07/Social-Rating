package dev.eyecrasher.socialrating;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

public final class RatingClientPlatform {
    private static Consumer<Minecraft> ticks = mc -> {}, joins = mc -> {}, leaves = mc -> {};
    private static BiConsumer<RatingWire.Reply, Minecraft> replies = (packet, mc) -> {};
    private static Consumer<KeyMapping> keys;
    public static void tick(Consumer<Minecraft> value) { ticks = value; }
    public static void joined(Consumer<Minecraft> value) { joins = value; }
    public static void disconnected(Consumer<Minecraft> value) { leaves = value; }
    public static void replyReceiver(BiConsumer<RatingWire.Reply, Minecraft> value) { replies = value; }
    public static KeyMapping key(KeyMapping value) { keys.accept(value); return value; }
    public RatingClientPlatform(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            keys = mapping -> { event.register(mapping); };
            new SocialRatingClient().initialize();
            RatingPlatform.clientReply = packet -> replies.accept(packet, Minecraft.getInstance());
        });
        NeoForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> { if (event.phase == TickEvent.Phase.END) ticks.accept(Minecraft.getInstance()); });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> joins.accept(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> leaves.accept(Minecraft.getInstance()));
    }
    public static boolean canSend() {
        var listener = Minecraft.getInstance().getConnection();
        return listener != null && RatingPlatform.canSendRequest(listener.getConnection());
    }
    public static void send(RatingWire.Request packet) { RatingPlatform.sendRequest(Minecraft.getInstance().getConnection().getConnection(), packet); }
}
