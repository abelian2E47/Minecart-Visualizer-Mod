package com.minecartvisualizer;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.UUID;

public record MinecartDataPayload(UUID uuid, Vec3d pos, Vec3d velocity, double speed, float yaw, int id,
                                  long serverTime, boolean removed) {

    public static void write(PacketByteBuf buf, MinecartDataPayload payload) {
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
    }

    public static MinecartDataPayload read(PacketByteBuf buf) {
        return new MinecartDataPayload(
                buf.readUuid(),
                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                buf.readDouble(),
                buf.readFloat(),
                buf.readInt(),
                buf.readLong(),
                buf.readBoolean()
        );
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
