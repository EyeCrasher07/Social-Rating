package dev.eyecrasher.socialrating;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class RatingWire {
    private RatingWire() {}

    public record Request(long id, String action, String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(new ResourceLocation("socialrating", "request_v3"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeLong(value.id); buf.writeUtf(value.action, 16); buf.writeUtf(value.json, 16384); },
                buf -> new Request(buf.readLong(), buf.readUtf(16), buf.readUtf(16384)));
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Reply(long id, String json) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = new Type<>(new ResourceLocation("socialrating", "reply_v3"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeLong(value.id); buf.writeUtf(value.json, 262144); },
                buf -> new Reply(buf.readLong(), buf.readUtf(262144)));
        @Override public Type<Reply> type() { return TYPE; }
    }
}
