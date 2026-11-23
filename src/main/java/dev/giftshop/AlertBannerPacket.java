package dev.giftshop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record AlertBannerPacket(String kind, String text, String detail, boolean live, int verticalPercent, int likes)
    implements CustomPacketPayload {
    static final Type<AlertBannerPacket> TYPE = new Type<>(Identifier.parse("fabric_gift_shop:portrait_alert"));
    static final StreamCodec<RegistryFriendlyByteBuf, AlertBannerPacket> CODEC = StreamCodec.composite(
        ByteBufCodecs.stringUtf8(12), AlertBannerPacket::kind,
        ByteBufCodecs.stringUtf8(600), AlertBannerPacket::text,
        ByteBufCodecs.stringUtf8(64), AlertBannerPacket::detail,
        ByteBufCodecs.BOOL, AlertBannerPacket::live,
        ByteBufCodecs.VAR_INT, AlertBannerPacket::verticalPercent,
        ByteBufCodecs.VAR_INT, AlertBannerPacket::likes, AlertBannerPacket::new);

    @Override public Type<AlertBannerPacket> type() { return TYPE; }
}
