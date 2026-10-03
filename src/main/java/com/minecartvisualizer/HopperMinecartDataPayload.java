package com.minecartvisualizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public record HopperMinecartDataPayload(UUID uuid, boolean enable, List<ItemStack> items,
                                        Optional<BlockPos> extractionBlock,
                                        List<AABB> extractionEntities) implements CustomPacketPayload {
    public static final Type<HopperMinecartDataPayload> ID = new CustomPacketPayload.Type<>(Minecartvisualizer.HOPPER_MINECART_DATA_PACKET_ID);

    public HopperMinecartDataPayload(UUID uuid, boolean enable, List<ItemStack> items) {
        this(uuid, enable, items, Optional.empty(), List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, HopperMinecartDataPayload> CODEC = StreamCodec.ofMember(
            (payload, buf) -> {
                buf.writeUUID(payload.uuid());
                buf.writeBoolean(payload.enable());
                for (int i = 0; i < 5; i++) {
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.items().get(i));
                }

                buf.writeBoolean(payload.extractionBlock().isPresent());
                payload.extractionBlock().ifPresent(pos -> {
                    buf.writeInt(pos.getX());
                    buf.writeInt(pos.getY());
                    buf.writeInt(pos.getZ());
                });

                buf.writeVarInt(payload.extractionEntities().size());
                for (AABB box : payload.extractionEntities()) {
                    buf.writeDouble(box.minX);
                    buf.writeDouble(box.minY);
                    buf.writeDouble(box.minZ);
                    buf.writeDouble(box.maxX);
                    buf.writeDouble(box.maxY);
                    buf.writeDouble(box.maxZ);
                }
            },
            buf -> {
                UUID uuid = buf.readUUID();
                boolean enable = buf.readBoolean();
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                }

                Optional<BlockPos> extractionBlock = Optional.empty();
                if (buf.readBoolean()) {
                    extractionBlock = Optional.of(new BlockPos(buf.readInt(), buf.readInt(), buf.readInt()));
                }

                int entityCount = buf.readVarInt();
                List<AABB> extractionEntities = new ArrayList<>(entityCount);
                for (int i = 0; i < entityCount; i++) {
                    extractionEntities.add(new AABB(
                            buf.readDouble(), buf.readDouble(), buf.readDouble(),
                            buf.readDouble(), buf.readDouble(), buf.readDouble()));
                }

                return new HopperMinecartDataPayload(uuid, enable, items, extractionBlock, extractionEntities);
            }
    );
}
