package dev.eyecrasher.socialrating;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

public final class RatingPlatform implements ModInitializer {
    @FunctionalInterface public interface RequestHandler { void accept(MinecraftServer server, ServerPlayer player, RatingWire.Request request); }
    public void onInitialize() { new SocialRatingMod().initialize(); }
    public static Path configDirectory() { return FabricLoader.getInstance().getConfigDir(); }
    public static void requestReceiver(RequestHandler receiver) {
        ServerPlayNetworking.registerGlobalReceiver(RatingWire.Request.TYPE.id(), (server, player, handler, buffer, sender) -> {
            RatingWire.Request request = RatingWire.Request.CODEC.decode(buffer);
            server.execute(() -> receiver.accept(server, player, request));
        });
    }
    public static boolean canSend(ServerPlayer player) { return ServerPlayNetworking.canSend(player, RatingWire.Reply.TYPE.id()); }
    public static void send(ServerPlayer player, RatingWire.Reply packet) {
        var buffer = PacketByteBufs.create(); RatingWire.Reply.CODEC.encode(buffer, packet);
        ServerPlayNetworking.send(player, RatingWire.Reply.TYPE.id(), buffer);
    }
    public static void starting(Consumer<MinecraftServer> action) { ServerLifecycleEvents.SERVER_STARTING.register(action::accept); }
    public static void stopped(Consumer<MinecraftServer> action) { ServerLifecycleEvents.SERVER_STOPPED.register(action::accept); }
    public static void commands(Consumer<CommandDispatcher<CommandSourceStack>> action) { CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> action.accept(dispatcher)); }
    public static void joined(BiConsumer<ServerPlayer, MinecraftServer> action) { ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> action.accept(handler.player, server)); }
    public static void disconnected(BiConsumer<ServerPlayer, MinecraftServer> action) { ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> action.accept(handler.player, server)); }
}
