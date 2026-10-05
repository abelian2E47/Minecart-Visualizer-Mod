package com.minecartvisualizer;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record TNTMinecartDataPayload(UUID uuid, int fuseTicks, boolean isExploded, Vec3d explosionPos, Float damageWobbleStrength) {

    public static void write(PacketByteBuf buf, TNTMinecartDataPayload payload) {
        buf.writeUuid(payload.uuid);
        buf.writeInt(payload.fuseTicks);
        buf.writeBoolean(payload.isExploded);
        //1.20.1 的 PacketByteBuf 还没有 writeVec3d，原样写成三个 double（与 1.20.2+ 的实现一致）
        buf.writeDouble(payload.explosionPos.x);
        buf.writeDouble(payload.explosionPos.y);
        buf.writeDouble(payload.explosionPos.z);
        buf.writeFloat(payload.damageWobbleStrength);
    }

    public static TNTMinecartDataPayload read(PacketByteBuf buf) {
        return new TNTMinecartDataPayload(
                buf.readUuid(),
                buf.readInt(),
                buf.readBoolean(),
                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readFloat()
        );
    }


    public List<MutableText> getInfoTexts(boolean[] enableSettings){
        List<MutableText> infoTexts = new ArrayList<>();
        MutableText damageWobbleStrengthText = Text.literal("Wobble:" + damageWobbleStrength()).setStyle(Style.EMPTY.withColor(0xE61717));
        MutableText fuseTicks = Text.literal("Fuse:" + fuseTicks()).setStyle(Style.EMPTY.withColor(0xE61717));
        if(enableSettings[0]){infoTexts.add(damageWobbleStrengthText);}
        if (enableSettings[1]){infoTexts.add(fuseTicks);}
        return infoTexts;
    }

}
