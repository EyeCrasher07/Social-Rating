package dev.eyecrasher.socialrating;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Bounded messages carried over the host's existing Minecraft connection. */
public final class RatingWire {
    private RatingWire() {}
    public static final class Type<T> {
        private final ResourceLocation id;
        public Type(ResourceLocation id) {
            this.id = id;
        }
        public ResourceLocation id() { return id; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            Type that = (Type) other;
            return java.util.Objects.equals(id, that.id);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id); }
}
    public static final class Codec<T> {
        private final BiConsumer<FriendlyByteBuf, T> writer;
        private final Function<FriendlyByteBuf, T> reader;
        public Codec(BiConsumer<FriendlyByteBuf, T> writer, Function<FriendlyByteBuf, T> reader) {
            this.writer = writer;
            this.reader = reader;
        }
        public BiConsumer<FriendlyByteBuf, T> writer() { return writer; }
        public Function<FriendlyByteBuf, T> reader() { return reader; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            Codec that = (Codec) other;
            return java.util.Objects.equals(writer, that.writer) && java.util.Objects.equals(reader, that.reader);
        }
        @Override public int hashCode() { return java.util.Objects.hash(writer, reader); }

        public void encode(FriendlyByteBuf buf, T value) { writer.accept(buf, value); }
        public T decode(FriendlyByteBuf buf) { return reader.apply(buf); }
    }
    public static final class Request {
        private final long id;
        private final String action;
        private final String json;
        public Request(long id, String action, String json) {
            this.id = id;
            this.action = action;
            this.json = json;
        }
        public long id() { return id; }
        public String action() { return action; }
        public String json() { return json; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            Request that = (Request) other;
            return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(action, that.action) && java.util.Objects.equals(json, that.json);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, action, json); }

        public static final Type<Request> TYPE = new Type<>(new ResourceLocation("socialrating", "request_v3"));
        public static final Codec<Request> CODEC = new Codec<>((buf, value) -> {
            buf.writeLong(value.id); buf.writeUtf(value.action, 16); buf.writeUtf(value.json, 16384);
        }, buf -> new Request(buf.readLong(), buf.readUtf(16), buf.readUtf(16384)));
    }
    public static final class Reply {
        private final long id;
        private final String json;
        public Reply(long id, String json) {
            this.id = id;
            this.json = json;
        }
        public long id() { return id; }
        public String json() { return json; }
        @Override public boolean equals(Object other) {
            if (other == this) return true;
            if (other == null || other.getClass() != getClass()) return false;
            Reply that = (Reply) other;
            return java.util.Objects.equals(id, that.id) && java.util.Objects.equals(json, that.json);
        }
        @Override public int hashCode() { return java.util.Objects.hash(id, json); }

        public static final Type<Reply> TYPE = new Type<>(new ResourceLocation("socialrating", "reply_v3"));
        public static final Codec<Reply> CODEC = new Codec<>((buf, value) -> {
            buf.writeLong(value.id); buf.writeUtf(value.json, 262144);
        }, buf -> new Reply(buf.readLong(), buf.readUtf(262144)));
    }
}
