package com.minecartvisualizer;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * 服务端监测到的"矿车被挤压（与方块/实体发生碰撞）"事件，一次性下发。
 *
 * <p>挤压的判据是矿车的动量真的被削掉了多少，而不是"碰到了东西"：服务端在
 * 矿车移动与推挤的真正出口处对比碰撞前后的速度，把差值（{@code deltaVelocity}）算好下发，
 * 客户端只按配置决定显示哪些字段与是否达到阈值，不再自己推算。</p>
 *
 * <ul>
 *     <li>{@code deltaVelocity}：碰撞造成的动量变化量（末速度 − 初速度，格/游戏刻）。
 *     数值大小即 {@link #deltaMomentum()}，用于阈值判定。</li>
 *     <li>{@code speedBefore} / {@code speedAfter}：碰撞前后的速度大小（格/游戏刻），
 *     两者之差即 {@link #deltaSpeed()}，也就是"速度变化量"。</li>
 *     <li>{@code blockTarget}：碰撞对象是方块还是实体；{@code targetId} 是它的注册名
 *     （方块如 {@code minecraft:stone}，实体如 {@code minecraft:zombie}）；
 *     {@code targetCustomName} 只在实体带有自定义名称时非空。</li>
 *     <li>{@code serverTime}：服务端世界时间（tick），客户端用它做报告冷却判定。</li>
 * </ul>
 */
public record MinecartCollisionPayload(UUID uuid, Vec3d pos, Vec3d deltaVelocity,
                                       double speedBefore, double speedAfter,
                                       boolean blockTarget, String targetId, String targetCustomName,
                                       Vec3d targetPos, long serverTime) implements CustomPayload {

    public static final Id<MinecartCollisionPayload> ID =
            new CustomPayload.Id<>(MinecartVisualizer.MINECART_COLLISION_PACKET_ID);

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

    /** 动量变化量的大小 |Δv|，阈值比较用的就是它。 */
    public double deltaMomentum() {
        return this.deltaVelocity.length();
    }

    /** 速度变化量：末速度模长 − 初速度模长（带符号，格/游戏刻）。 */
    public double deltaSpeed() {
        return this.speedAfter() - this.speedBefore();
    }
}
