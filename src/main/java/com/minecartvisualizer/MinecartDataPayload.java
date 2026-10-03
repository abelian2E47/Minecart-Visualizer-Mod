package com.minecartvisualizer;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.UUID;

/**
 * 服务端每 tick 下发的矿车状态，是客户端所有矿车显示的权威数据源。
 *
 * <p>新增两个字段，用于消除客户端自行推算带来的显示误差：</p>
 * <ul>
 *     <li>{@code serverTime}：服务端世界时间（tick）。客户端用它做数据时效判定、
 *     追踪器运行时长、连续吸取持续 tick 数与计数器统计，不再使用客户端自己的
 *     {@code ClientWorld#getTime()}（客户端卡顿、单机暂停、掉帧都会让它与真实进度漂移）。</li>
 *     <li>{@code removed}：矿车被真正销毁（{@code KILLED} / {@code DISCARDED}）时
 *     由服务端补发的最后一次数据，客户端据此判定"矿车被摧毁"，
 *     不再依据客户端实体列表（离开视距或区块卸载会被误判为摧毁）。</li>
 * </ul>
 */
public record MinecartDataPayload(UUID uuid, Vec3d pos, Vec3d velocity, double speed, float yaw, int id,
                                  long serverTime, boolean removed) implements CustomPayload {

    public static final Id<MinecartDataPayload> ID = new CustomPayload.Id<>(MinecartVisualizer.MINECART_DATA_PACKET_ID);

    public static final PacketCodec<ByteBuf, Vec3d> VEC3D_CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeDouble(value.x);
                buf.writeDouble(value.y);
                buf.writeDouble(value.z);
            },
            buf -> new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble())
    );

    public static final PacketCodec<RegistryByteBuf, MinecartDataPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeUuid(payload.uuid());
                buf.writeDouble(payload.pos().x);
                buf.writeDouble(payload.pos().y);
                buf.writeDouble(payload.pos().z);
                buf.writeDouble(payload.velocity().x);
                buf.writeDouble(payload.velocity().y);
                buf.writeDouble(payload.velocity().z);
                buf.writeDouble(payload.speed());
                buf.writeFloat(payload.yaw());
                buf.writeInt(payload.id());
                buf.writeLong(payload.serverTime());
                buf.writeBoolean(payload.removed());
            },
            buf -> new MinecartDataPayload(
                    buf.readUuid(),
                    new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readLong(),
                    buf.readBoolean()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }


    public ArrayList<MutableText> getInfoTexts(int accuracy, boolean[] EnableFunctions) {
        ArrayList<MutableText> infoTexts = new ArrayList<>();

        if (this.pos() == null) {
            infoTexts.add(Text.translatable("info.minecartvisualizer.pos").append(Text.literal("unknown")));
        } else if (EnableFunctions[0]) {
            infoTexts.add(Text.translatable("info.minecartvisualizer.pos")
                    .append(FormatTools.formatVec(this.pos(), accuracy, false)));
        }

        if (this.velocity() == null) {
            infoTexts.add(Text.translatable("info.minecartvisualizer.velocity")
                    .append(Text.literal("unknown").formatted(Formatting.GRAY)));
        } else if (EnableFunctions[1]) {
            Vec3d v = this.velocity();
            MutableText velocityText = Text.translatable("info.minecartvisualizer.velocity");

            if (Double.isInfinite(v.x) || Double.isInfinite(v.y) || Double.isInfinite(v.z)) {
                velocityText.append(Text.literal("∞"));
            }
            else if (Double.isNaN(v.x) || Double.isNaN(v.y) || Double.isNaN(v.z)) {
                velocityText.append(Text.literal("NaN"));
            }
            else {
                velocityText.append(FormatTools.formatVec(v, accuracy, true));
            }

            infoTexts.add(velocityText);
        }

        if (EnableFunctions[2]) {
            infoTexts.add(Text.translatable("info.minecartvisualizer.yaw")
                    .append(FormatTools.formatDouble(this.yaw(), accuracy, false)));
        }

        if (EnableFunctions[3]){
            if (EnableFunctions[4]){
                infoTexts.add(Text.translatable("info.minecartvisualizer.speed")
                        .append(FormatTools.formatDouble(this.speed()*20, accuracy, true)).append("m/s"));
            }else {
                infoTexts.add(Text.translatable("info.minecartvisualizer.speed")
                        .append(FormatTools.formatDouble(this.speed(), accuracy, true)).append("m/gt"));
            }
        }

        return infoTexts;
    }


}
