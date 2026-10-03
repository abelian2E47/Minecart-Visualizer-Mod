package com.minecartvisualizer;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record MinecartCollisionPayload(UUID uuid, Vec3 pos, Vec3 deltaVelocity,
                                       double speedBefore, double speedAfter,
                                       boolean blockTarget, String targetId, String targetCustomName,
                                       Vec3 targetPos, long serverTime) implements CustomPacketPayload {

    public static final Type<MinecartCollisionPayload> ID =
            new CustomPacketPayload.Type<>(Minecartvisualizer.MINECART_COLLISION_PACKET_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, MinecartCollisionPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUUID(payload.uuid());
                buf.writeDouble(payload.pos().x);
                buf.writeDouble(payload.pos().y);
                buf.writeDouble(payload.pos().z);
                buf.writeDouble(payload.deltaVelocity().x);
                buf.writeDouble(payload.deltaVelocity().y);
                buf.writeDouble(payload.deltaVelocity().z);
                buf.writeDouble(payload.speedBefore());
                buf.writeDouble(payload.speedAfter());
                buf.writeBoolean(payload.blockTarget());
                buf.writeUtf(payload.targetId());
                buf.writeUtf(payload.targetCustomName());
                buf.writeDouble(payload.targetPos().x);
                buf.writeDouble(payload.targetPos().y);
                buf.writeDouble(payload.targetPos().z);
                buf.writeLong(payload.serverTime());
            },
            buf -> new MinecartCollisionPayload(
                    buf.readUUID(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readBoolean(),
                    buf.readUtf(),
                    buf.readUtf(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readLong()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public double deltaMomentum() {
        return this.deltaVelocity.length();
    }

    public double deltaSpeed() {
        return this.speedAfter() - this.speedBefore();
    }
}
