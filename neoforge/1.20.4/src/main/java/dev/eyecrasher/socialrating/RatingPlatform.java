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
    private static void registerNetwork(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener((net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent event) -> {
            var registrar = event.registrar("socialrating").versioned("3").optional();
            registrar.play(RatingWire.Request.TYPE.id(), buf -> new RequestPayload(RatingWire.Request.CODEC.decode(buf)),
                    handler -> handler.server((payload, context) -> context.workHandler().execute(() -> context.player().filter(ServerPlayer.class::isInstance).ifPresent(entity -> {
                        var player = (ServerPlayer) entity;
                        requests.accept(player.getServer(), player, payload.packet());
                    }))));
            registrar.play(RatingWire.Reply.TYPE.id(), buf -> new ReplyPayload(RatingWire.Reply.CODEC.decode(buf)),
                    handler -> handler.client((payload, context) -> context.workHandler().execute(() -> clientReply.accept(payload.packet()))));
        });
    }
    private static final class RequestPayload implements net.minecraft.network.protocol.common.custom.CustomPacketPayload  {
        private final RatingWire.Request packet;
        public RequestPayload(RatingWire.Request packet) {
            this.packet = packet;
        }
        public RatingWire.Request packet() { return packet; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            RequestPayload that = (RequestPayload) other;
            return java.util.Objects.equals(packet, that.packet);
        }
        @Override public int hashCode() { return java.util.Objects.hash(packet); }

        public net.minecraft.resources.ResourceLocation id() { return RatingWire.Request.TYPE.id(); }
        public void write(net.minecraft.network.FriendlyByteBuf buf) { RatingWire.Request.CODEC.encode(buf, packet); }
    }
    private static final class ReplyPayload implements net.minecraft.network.protocol.common.custom.CustomPacketPayload  {
        private final RatingWire.Reply packet;
        public ReplyPayload(RatingWire.Reply packet) {
            this.packet = packet;
        }
        public RatingWire.Reply packet() { return packet; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            ReplyPayload that = (ReplyPayload) other;
            return java.util.Objects.equals(packet, that.packet);
        }
        @Override public int hashCode() { return java.util.Objects.hash(packet); }

        public net.minecraft.resources.ResourceLocation id() { return RatingWire.Reply.TYPE.id(); }
        public void write(net.minecraft.network.FriendlyByteBuf buf) { RatingWire.Reply.CODEC.encode(buf, packet); }
    }
    public static boolean canSend(ServerPlayer player) { return net.neoforged.neoforge.network.registration.NetworkRegistry.getInstance().isConnected(player.connection, RatingWire.Reply.TYPE.id()); }
    public static void send(ServerPlayer player, RatingWire.Reply packet) { net.neoforged.neoforge.network.PacketDistributor.PLAYER.with(player).send(new ReplyPayload(packet)); }
    static boolean canSendRequest(net.minecraft.client.multiplayer.ClientPacketListener listener) { return net.neoforged.neoforge.network.registration.NetworkRegistry.getInstance().isConnected(listener, RatingWire.Request.TYPE.id()); }
    static void sendRequest(RatingWire.Request packet) { net.neoforged.neoforge.network.PacketDistributor.SERVER.noArg().send(new RequestPayload(packet)); }
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
