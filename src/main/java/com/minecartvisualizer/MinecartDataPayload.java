package com.minecartvisualizer;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record MinecartDataPayload(UUID uuid, Vec3 pos, Vec3 velocity, double speed, float yaw, int id,
                                  long serverTime, boolean removed) implements CustomPacketPayload {

    public static final Type<MinecartDataPayload> ID = new CustomPacketPayload.Type<>(Minecartvisualizer.MINECART_DATA_PACKET_ID);

    public static final StreamCodec<ByteBuf, Vec3> VEC3D_CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeDouble(value.x);
                buf.writeDouble(value.y);
                buf.writeDouble(value.z);
            },
            buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MinecartDataPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUUID(payload.uuid());
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
                    buf.readUUID(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readDouble(),
                    buf.readFloat(),
                    buf.readInt(),
                    buf.readLong(),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public ArrayList<MutableComponent> getInfoTexts(int accuracy, boolean[] EnableFunctions) {
        ArrayList<MutableComponent> infoTexts = new ArrayList<>();

        if (this.pos() == null) {
            infoTexts.add(Component.translatable("info.minecartvisualizer.pos").append(Component.literal("unknown")));
        } else if (EnableFunctions[0]) {
            infoTexts.add(Component.translatable("info.minecartvisualizer.pos")
                    .append(FormatTools.formatVec(this.pos(), accuracy, false)));
        }

        if (this.velocity() == null) {
            infoTexts.add(Component.translatable("info.minecartvisualizer.velocity")
                    .append(Component.literal("unknown").withStyle(ChatFormatting.GRAY)));
        } else if (EnableFunctions[1]) {
            Vec3 v = this.velocity();
            MutableComponent velocityText = Component.translatable("info.minecartvisualizer.velocity");

            if (Double.isInfinite(v.x) || Double.isInfinite(v.y) || Double.isInfinite(v.z)) {
                velocityText.append(Component.literal("∞"));
            }
            else if (Double.isNaN(v.x) || Double.isNaN(v.y) || Double.isNaN(v.z)) {
                velocityText.append(Component.literal("NaN"));
            }
            else {
                velocityText.append(FormatTools.formatVec(v, accuracy, true));
            }

            infoTexts.add(velocityText);
        }

        if (EnableFunctions[2]) {
            infoTexts.add(Component.translatable("info.minecartvisualizer.yaw")
                    .append(FormatTools.formatDouble(this.yaw(), accuracy, false)));
        }

        if (EnableFunctions[3]){
            if (EnableFunctions[4]){
                infoTexts.add(Component.translatable("info.minecartvisualizer.speed")
                        .append(FormatTools.formatDouble(this.speed()*20, accuracy, true)).append("m/s"));
            }else {
                infoTexts.add(Component.translatable("info.minecartvisualizer.speed")
                        .append(FormatTools.formatDouble(this.speed(), accuracy, true)).append("m/gt"));
            }
        }

        return infoTexts;
    }

}
