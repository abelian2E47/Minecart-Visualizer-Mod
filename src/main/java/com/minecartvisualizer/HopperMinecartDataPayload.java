package com.minecartvisualizer;

import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record HopperMinecartDataPayload(UUID uuid, boolean enable, List<ItemStack> items,
                                        Optional<BlockPos> extractionBlock,
                                        List<Box> extractionEntities) {
    
    public HopperMinecartDataPayload(UUID uuid, boolean enable, List<ItemStack> items) {
        this(uuid, enable, items, Optional.empty(), List.of());
    }

    public static void write(PacketByteBuf buf, HopperMinecartDataPayload payload) {
        buf.writeUuid(payload.uuid());
        buf.writeBoolean(payload.enable());
        for (int i = 0; i < 5; i++) {
            buf.writeItemStack(payload.items().get(i));
        }

        buf.writeBoolean(payload.extractionBlock().isPresent());
        payload.extractionBlock().ifPresent(pos -> {
            buf.writeInt(pos.getX());
            buf.writeInt(pos.getY());
            buf.writeInt(pos.getZ());
        });

        buf.writeVarInt(payload.extractionEntities().size());
        for (Box box : payload.extractionEntities()) {
            buf.writeDouble(box.minX);
            buf.writeDouble(box.minY);
            buf.writeDouble(box.minZ);
            buf.writeDouble(box.maxX);
            buf.writeDouble(box.maxY);
            buf.writeDouble(box.maxZ);
        }
    }

    public static HopperMinecartDataPayload read(PacketByteBuf buf) {
        UUID uuid = buf.readUuid();
        boolean enable = buf.readBoolean();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            items.add(buf.readItemStack());
        }

        Optional<BlockPos> extractionBlock = Optional.empty();
        if (buf.readBoolean()) {
            extractionBlock = Optional.of(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
        }

        int entityCount = buf.readVarInt();
        List<Box> extractionEntities = new ArrayList<>(entityCount);
        for (int i = 0; i < entityCount; i++) {
            extractionEntities.add(new Box(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }

        return new HopperMinecartDataPayload(uuid, enable, items, extractionBlock, extractionEntities);
    }
}
