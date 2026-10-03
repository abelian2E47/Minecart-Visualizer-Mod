package com.minecartvisualizer;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

public record MinecartCollisionPayload(UUID uuid, Vec3d pos, Vec3d deltaVelocity,
                                       double speedBefore, double speedAfter,
                                       boolean blockTarget, String targetId, String targetCustomName,
                                       Vec3d targetPos, long serverTime) implements CustomPayload {

    public static final Id<MinecartCollisionPayload> ID =
            new CustomPayload.Id<>(Minecartvisualizer.MINECART_COLLISION_PACKET_ID);

    public static final PacketCodec<RegistryByteBuf, MinecartCollisionPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
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
            },
            buf -> new MinecartCollisionPayload(
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
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public double deltaMomentum() {
        return this.deltaVelocity.length();
    }

    public double deltaSpeed() {
        return this.speedAfter() - this.speedBefore();
    }
}
