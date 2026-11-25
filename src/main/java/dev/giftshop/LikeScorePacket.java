package dev.giftshop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record LikeScorePacket(long total) implements CustomPacketPayload {
    static final Type<LikeScorePacket> TYPE = new Type<>(Identifier.parse("fabric_gift_shop:like_score"));
    static final StreamCodec<RegistryFriendlyByteBuf, LikeScorePacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, LikeScorePacket::total, LikeScorePacket::new);
    @Override public Type<LikeScorePacket> type() { return TYPE; }
}
