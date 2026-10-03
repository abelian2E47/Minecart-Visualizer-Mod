package com.minecartvisualizer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record TNTMinecartDataPayload(UUID uuid, int fuseTicks, boolean isExploded, Vec3 explosionPos, Float damageWobbleStrength) implements CustomPacketPayload {
    public static final Type<TNTMinecartDataPayload> ID = new CustomPacketPayload.Type<>(Minecartvisualizer.TNT_MINECART_DATA_PACKET_ID);

    @Override
    public Type<? extends CustomPacketPayload> type() { return ID; }

    public static final StreamCodec<RegistryFriendlyByteBuf, TNTMinecartDataPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUUID(payload.uuid);
                buf.writeInt(payload.fuseTicks);
                buf.writeBoolean(payload.isExploded);
                Vec3.STREAM_CODEC.encode(buf, payload.explosionPos);
                buf.writeFloat(payload.damageWobbleStrength);
            },
            buf -> new TNTMinecartDataPayload(
                    buf.readUUID(),
                    buf.readInt(),
                    buf.readBoolean(),
                    Vec3.STREAM_CODEC.decode(buf),
                    buf.readFloat()
            )
    );


    public List<MutableComponent> getInfoTexts(boolean[] enableSettings){
        List<MutableComponent> infoTexts = new ArrayList<>();
        MutableComponent damageWobbleStrengthText = Component.literal("Wobble:" + damageWobbleStrength()).setStyle(Style.EMPTY.withColor(0xE61717));
        MutableComponent fuseTicks = Component.literal("Fuse:" + fuseTicks()).setStyle(Style.EMPTY.withColor(0xE61717));
        if(enableSettings[0]){infoTexts.add(damageWobbleStrengthText);}
        if (enableSettings[1]){infoTexts.add(fuseTicks);}
        return infoTexts;
    }

}
