package com.minecartvisualizer;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public record MinecartCollisionPayload(UUID uuid, Vec3d pos, Vec3d deltaVelocity,
                                       double speedBefore, double speedAfter,
                                       boolean blockTarget, String targetId, String targetCustomName,
                                       Vec3d targetPos, long serverTime) {

    public static void write(PacketByteBuf buf, MinecartCollisionPayload payload) {
        buf.writeUuid(payload.uuid());
        buf.writeDouble(payload.pos().x);
        buf.writeDouble(payload.pos().y);
        buf.writeDouble(payload.pos().z);
        buf.writeDouble(payload.deltaVelocity().x);
        buf.writeDouble(payload.deltaVelocity().y);
        buf.writeDouble(payload.deltaVelocity().z);
        buf.writeDouble(payload.speedBefore());
        buf.writeDouble(payload.speedAfter());
        buf.writeBoolean(payload.blockTarget());
        buf.writeString(payload.targetId());
        buf.writeString(payload.targetCustomName());
        buf.writeDouble(payload.targetPos().x);
        buf.writeDouble(payload.targetPos().y);
        buf.writeDouble(payload.targetPos().z);
        buf.writeLong(payload.serverTime());
    }

    public static MinecartCollisionPayload read(PacketByteBuf buf) {
        return new MinecartCollisionPayload(
                buf.readUuid(),
                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean(),
                buf.readString(),
                buf.readString(),
                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readLong()
        );
    }

    public double deltaMomentum() {
        return this.deltaVelocity.length();
    }

    public double deltaSpeed() {
        return this.speedAfter() - this.speedBefore();
    }
}
