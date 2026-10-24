package dev.eyecrasher.socialrating;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;


public final class RatingClientPlatform {
    private static Consumer<Minecraft> ticks = mc -> {}, joins = mc -> {}, leaves = mc -> {};
    private static BiConsumer<RatingWire.Reply, Minecraft> replies = (packet, mc) -> {};
    private static Consumer<KeyMapping> keys;
    public static void tick(Consumer<Minecraft> value) { ticks = value; }
    public static void joined(Consumer<Minecraft> value) { joins = value; }
    public static void disconnected(Consumer<Minecraft> value) { leaves = value; }
    public static void replyReceiver(BiConsumer<RatingWire.Reply, Minecraft> value) { replies = value; }
    public static KeyMapping key(KeyMapping value) { keys.accept(value); return value; }
    public static void register(net.minecraftforge.eventbus.api.IEventBus modBus) {
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            keys = net.minecraftforge.client.ClientRegistry::registerKeyBinding;
            new SocialRatingClient().initialize();
            RatingPlatform.clientReply = packet -> replies.accept(packet, Minecraft.getInstance());
        }));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> { if (event.phase == TickEvent.Phase.END) ticks.accept(Minecraft.getInstance()); });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggedInEvent event) -> joins.accept(Minecraft.getInstance()));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggedOutEvent event) -> leaves.accept(Minecraft.getInstance()));
    }
    public static boolean canSend() {
        var listener = Minecraft.getInstance().getConnection();
        return listener != null && RatingPlatform.canSendRequest(listener.getConnection());
    }
    public static void send(RatingWire.Request packet) { RatingPlatform.sendRequest(Minecraft.getInstance().getConnection().getConnection(), packet); }
}
