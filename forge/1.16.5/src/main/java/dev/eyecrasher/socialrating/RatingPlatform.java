package dev.eyecrasher.socialrating;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.player.ServerPlayerEntity;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

@Mod("socialrating")
public final class RatingPlatform {
    @FunctionalInterface
    public interface RequestHandler { void accept(MinecraftServer server, ServerPlayerEntity player, RatingWire.Request request); }
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
    private static net.minecraftforge.fml.network.event.EventNetworkChannel requestsChannel, repliesChannel;
    private static net.minecraftforge.fml.network.event.EventNetworkChannel channel(net.minecraft.util.ResourceLocation id) {
        java.util.function.Predicate<String> accepts = version -> "3".equals(version)
                || net.minecraftforge.fml.network.NetworkRegistry.ABSENT.equals(version)
                || net.minecraftforge.fml.network.NetworkRegistry.ACCEPTVANILLA.equals(version);
        return net.minecraftforge.fml.network.NetworkRegistry.newEventChannel(id, () -> "3", accepts, accepts);
    }
    private static void registerNetwork() {
        requestsChannel = channel(RatingWire.Request.TYPE.id());
        repliesChannel = channel(RatingWire.Reply.TYPE.id());
        requestsChannel.addListener((net.minecraftforge.fml.network.NetworkEvent.ClientCustomPayloadEvent event) -> {
            var context = event.getSource().get();
            var request = RatingWire.Request.CODEC.decode(event.getPayload());
            context.setPacketHandled(true);
            var player = context.getSender();
            if (player != null) context.enqueueWork(() -> requests.accept(player.getServer(), player, request));
        });
        repliesChannel.addListener((net.minecraftforge.fml.network.NetworkEvent.ServerCustomPayloadEvent event) -> {
            var context = event.getSource().get();
            var reply = RatingWire.Reply.CODEC.decode(event.getPayload());
            context.setPacketHandled(true);
            context.enqueueWork(() -> clientReply.accept(reply));
        });
    }
    public static boolean canSend(ServerPlayerEntity player) { return repliesChannel.isRemotePresent(player.connection.connection); }
    public static void send(ServerPlayerEntity player, RatingWire.Reply packet) {
        var buffer = new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer());
        RatingWire.Reply.CODEC.encode(buffer, packet);
        player.connection.send(new net.minecraft.network.play.server.SCustomPayloadPlayPacket(RatingWire.Reply.TYPE.id(), buffer));
    }
    static boolean canSendRequest(net.minecraft.network.NetworkManager connection) { return requestsChannel.isRemotePresent(connection); }
    static void sendRequest(net.minecraft.network.NetworkManager connection, RatingWire.Request packet) {
        var buffer = new net.minecraft.network.PacketBuffer(io.netty.buffer.Unpooled.buffer());
        RatingWire.Request.CODEC.encode(buffer, packet);
        connection.send(new net.minecraft.network.play.client.CCustomPayloadPacket(RatingWire.Request.TYPE.id(), buffer));
    }
    public static void starting(Consumer<MinecraftServer> action) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((FMLServerStartingEvent event) -> action.accept(event.getServer())); }
    public static void stopped(Consumer<MinecraftServer> action) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((FMLServerStoppedEvent event) -> action.accept(event.getServer())); }
    public static void commands(Consumer<CommandDispatcher<CommandSource>> action) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> action.accept(event.getDispatcher())); }
    public static void joined(BiConsumer<ServerPlayerEntity, MinecraftServer> action) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> { if (event.getEntity() instanceof ServerPlayerEntity player) action.accept(player, player.level.getServer()); });
    }
    public static void disconnected(BiConsumer<ServerPlayerEntity, MinecraftServer> action) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> { if (event.getEntity() instanceof ServerPlayerEntity player) action.accept(player, player.level.getServer()); });
    }

}
