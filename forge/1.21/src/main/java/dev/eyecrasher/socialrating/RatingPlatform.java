package dev.eyecrasher.socialrating;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

@Mod("socialrating")
public final class RatingPlatform {
    @FunctionalInterface
    public interface RequestHandler { void accept(MinecraftServer server, ServerPlayer player, RatingWire.Request request); }
    private static RequestHandler requests;
    static Consumer<RatingWire.Reply> clientReply = packet -> {};
    public static void requestReceiver(RequestHandler receiver) { requests = receiver; }
    public static Path configDirectory() { return FMLPaths.CONFIGDIR.get(); }
    private static Channel<CustomPacketPayload> channel;
    public RatingPlatform() {
        var context = FMLJavaModLoadingContext.get();
        new SocialRatingMod().initialize();
        var play = ChannelBuilder.named("socialrating:ratings_v3").networkProtocolVersion(3).optional().payloadChannel().play();
        play.serverbound().addMain(RatingWire.Request.TYPE, RatingWire.Request.CODEC, (packet, network) -> {
            var player = network.getSender();
            if (player != null) requests.accept(player.level().getServer(), player, packet);
        });
        channel = play.clientbound().addMain(RatingWire.Reply.TYPE, RatingWire.Reply.CODEC,
                (packet, network) -> clientReply.accept(packet)).build();
        if (FMLEnvironment.dist == Dist.CLIENT) RatingClientPlatform.register(context.getModEventBus());
    }
    public static boolean canSend(ServerPlayer player) { return channel.isRemotePresent(player.connection.getConnection()); }
    public static void send(ServerPlayer player, RatingWire.Reply packet) { channel.send(packet, player.connection.getConnection()); }
    static Channel<CustomPacketPayload> channel() { return channel; }

    public static void starting(Consumer<MinecraftServer> action) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent event) -> action.accept(event.getServer())); }
    public static void stopped(Consumer<MinecraftServer> action) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> action.accept(event.getServer())); }
    public static void commands(Consumer<CommandDispatcher<CommandSourceStack>> action) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> action.accept(event.getDispatcher())); }
    public static void joined(BiConsumer<ServerPlayer, MinecraftServer> action) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> { if (event.getEntity() instanceof ServerPlayer player) action.accept(player, player.level().getServer()); });
    }
    public static void disconnected(BiConsumer<ServerPlayer, MinecraftServer> action) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> { if (event.getEntity() instanceof ServerPlayer player) action.accept(player, player.level().getServer()); });
    }

}
