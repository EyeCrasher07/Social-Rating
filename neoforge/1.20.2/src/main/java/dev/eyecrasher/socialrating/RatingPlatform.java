package dev.eyecrasher.socialrating;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@Mod("socialrating")
public final class RatingPlatform {
    @FunctionalInterface
    public interface RequestHandler { void accept(MinecraftServer server, ServerPlayer player, RatingWire.Request request); }
    private static RequestHandler requests;
    static Consumer<RatingWire.Reply> clientReply = packet -> {};
    public static void requestReceiver(RequestHandler receiver) { requests = receiver; }
    public static Path configDirectory() { return FMLPaths.CONFIGDIR.get(); }
    public RatingPlatform(IEventBus modBus) {
        new SocialRatingMod().initialize();
        registerNetwork(modBus);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) new RatingClientPlatform(modBus);
    }
    private static net.neoforged.neoforge.network.event.EventNetworkChannel requestsChannel, repliesChannel;
    private static net.neoforged.neoforge.network.event.EventNetworkChannel channel(net.minecraft.resources.ResourceLocation id) {
        java.util.function.Predicate<String> accepts = version -> "3".equals(version)
                || net.neoforged.neoforge.network.NetworkRegistry.ABSENT.version().equals(version)
                || net.neoforged.neoforge.network.NetworkRegistry.ACCEPTVANILLA.equals(version);
        return net.neoforged.neoforge.network.NetworkRegistry.newEventChannel(id, () -> "3", accepts, accepts);
    }
    private static void registerNetwork(IEventBus modBus) {
        requestsChannel = channel(RatingWire.Request.TYPE.id());
        repliesChannel = channel(RatingWire.Reply.TYPE.id());
        requestsChannel.addListener((net.neoforged.neoforge.network.NetworkEvent.ClientCustomPayloadEvent event) -> {
            var context = event.getSource();
            var request = RatingWire.Request.CODEC.decode(event.getPayload());
            context.setPacketHandled(true);
            var player = context.getSender();
            if (player != null) context.enqueueWork(() -> requests.accept(player.getServer(), player, request));
        });
        repliesChannel.addListener((net.neoforged.neoforge.network.NetworkEvent.ServerCustomPayloadEvent event) -> {
            var context = event.getSource();
            var reply = RatingWire.Reply.CODEC.decode(event.getPayload());
            context.setPacketHandled(true);
            context.enqueueWork(() -> clientReply.accept(reply));
        });
    }
    public static boolean canSend(ServerPlayer player) { return repliesChannel.isRemotePresent(player.connection.connection); }
    public static void send(ServerPlayer player, RatingWire.Reply packet) {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        RatingWire.Reply.CODEC.encode(buffer, packet);
        player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(new net.neoforged.neoforge.network.custom.payload.SimplePayload(buffer, RatingWire.Reply.TYPE.id(), 0)));
    }
    static boolean canSendRequest(net.minecraft.network.Connection connection) { return requestsChannel.isRemotePresent(connection); }
    static void sendRequest(net.minecraft.network.Connection connection, RatingWire.Request packet) {
        var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        RatingWire.Request.CODEC.encode(buffer, packet);
        connection.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(new net.neoforged.neoforge.network.custom.payload.SimplePayload(buffer, RatingWire.Request.TYPE.id(), 0)));
    }
    public static void starting(Consumer<MinecraftServer> action) { NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> action.accept(event.getServer())); }
    public static void stopped(Consumer<MinecraftServer> action) { NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> action.accept(event.getServer())); }
    public static void commands(Consumer<CommandDispatcher<CommandSourceStack>> action) { NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> action.accept(event.getDispatcher())); }
    public static void joined(BiConsumer<ServerPlayer, MinecraftServer> action) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> { if (event.getEntity() instanceof ServerPlayer player) action.accept(player, player.level().getServer()); });
    }
    public static void disconnected(BiConsumer<ServerPlayer, MinecraftServer> action) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> { if (event.getEntity() instanceof ServerPlayer player) action.accept(player, player.level().getServer()); });
    }

}
