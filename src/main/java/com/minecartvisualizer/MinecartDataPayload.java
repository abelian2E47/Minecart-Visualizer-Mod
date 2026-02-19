package com.minecartvisualizer;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.UUID;

public record MinecartDataPayload(UUID uuid, Vec3d pos, Vec3d velocity, double speed, float yaw, int id) implements CustomPayload {

    public static final Id<MinecartDataPayload> ID = new CustomPayload.Id<>(MinecartVisualizer.MINECART_DATA_PACKET_ID);

    public static final PacketCodec<ByteBuf, Vec3d> VEC3D_CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeDouble(value.x);
                buf.writeDouble(value.y);
                buf.writeDouble(value.z);
            },
            buf -> new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble())
    );

    public static final PacketCodec<RegistryByteBuf, MinecartDataPayload> CODEC = PacketCodec.tuple(
            Uuids.PACKET_CODEC, MinecartDataPayload::uuid,
            VEC3D_CODEC.cast(), MinecartDataPayload::pos,
            VEC3D_CODEC.cast(), MinecartDataPayload::velocity,
            PacketCodecs.DOUBLE, MinecartDataPayload::speed,
            PacketCodecs.FLOAT, MinecartDataPayload::yaw,
            PacketCodecs.VAR_INT, MinecartDataPayload::id,
            MinecartDataPayload::new
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
