package dev.eyecrasher.socialrating;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

public final class RatingClientPlatform implements ClientModInitializer {
    public void onInitializeClient() { new SocialRatingClient().initialize(); }
    public static void replyReceiver(BiConsumer<RatingWire.Reply, Minecraft> receiver) {
        ClientPlayNetworking.registerGlobalReceiver(RatingWire.Reply.TYPE.id(), (mc, handler, buffer, sender) -> {
            RatingWire.Reply packet = RatingWire.Reply.CODEC.decode(buffer);
            mc.execute(() -> receiver.accept(packet, mc));
        });
    }
    public static boolean canSend() { return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(RatingWire.Request.TYPE.id()); }
    public static void send(RatingWire.Request packet) {
        var buffer = PacketByteBufs.create(); RatingWire.Request.CODEC.encode(buffer, packet);
        ClientPlayNetworking.send(RatingWire.Request.TYPE.id(), buffer);
    }
    public static KeyMapping key(KeyMapping value) { return KeyBindingHelper.registerKeyBinding(value); }
    public static void tick(Consumer<Minecraft> value) { ClientTickEvents.END_CLIENT_TICK.register(value::accept); }
    public static void joined(Consumer<Minecraft> value) { ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> value.accept(mc)); }
    public static void disconnected(Consumer<Minecraft> value) { ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> value.accept(mc)); }
}
