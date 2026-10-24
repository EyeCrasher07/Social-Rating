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

@Mod("socialrating")
public final class RatingPlatform {
    @FunctionalInterface
    public interface RequestHandler { void accept(MinecraftServer server, ServerPlayer player, RatingWire.Request request); }
    private static RequestHandler requests;
    static Consumer<RatingWire.Reply> clientReply = packet -> {};
    public static void requestReceiver(RequestHandler receiver) { requests = receiver; }
    public static Path configDirectory() { return FMLPaths.CONFIGDIR.get(); }
    public RatingPlatform() {
        var context = FMLJavaModLoadingContext.get();
        new SocialRatingMod().initialize();
        registerNetwork();
        if (FMLEnvironment.dist == Dist.CLIENT) RatingClientPlatform.register(context.getModEventBus());
    }
    private static net.minecraftforge.network.EventNetworkChannel requestsChannel, repliesChannel;
    private static void registerNetwork() {
        requestsChannel = net.minecraftforge.network.ChannelBuilder.named(RatingWire.Request.TYPE.id()).networkProtocolVersion(3).optional().eventNetworkChannel();
        repliesChannel = net.minecraftforge.network.ChannelBuilder.named(RatingWire.Reply.TYPE.id()).networkProtocolVersion(3).optional().eventNetworkChannel();
        requestsChannel.addListener((net.minecraftforge.event.network.CustomPayloadEvent event) -> {
            var context = event.getSource();
            if (!context.isServerSide()) return;
            var request = RatingWire.Request.CODEC.decode(event.getPayload());
            context.setPacketHandled(true);
            var player = context.getSender();
            if (player != null) context.enqueueWork(() -> requests.accept(player.getServer(), player, request));
        });
        repliesChannel.addListener((net.minecraftforge.event.network.CustomPayloadEvent event) -> {
            var context = event.getSource();
            if (!context.isClientSide()) return;
            var reply = RatingWire.Reply.CODEC.decode(event.getPayload());
            context.setPacketHandled(true);
            context.enqueueWork(() -> clientReply.accept(reply));
        });
    }
    public static boolean canSend(ServerPlayer player) { return repliesChannel.isRemotePresent(player.connection.getConnection()); }
    public static void send(ServerPlayer player, RatingWire.Reply packet) {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        RatingWire.Reply.CODEC.encode(buffer, packet);
        player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(new net.minecraft.network.protocol.common.custom.DiscardedPayload(RatingWire.Reply.TYPE.id(), buffer)));
    }
    static boolean canSendRequest(net.minecraft.network.Connection connection) { return requestsChannel.isRemotePresent(connection); }
    static void sendRequest(net.minecraft.network.Connection connection, RatingWire.Request packet) {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        RatingWire.Request.CODEC.encode(buffer, packet);
        connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(new net.minecraft.network.protocol.common.custom.DiscardedPayload(RatingWire.Request.TYPE.id(), buffer)));
    }
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
