package com.minecartvisualizer;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record HopperMinecartDataPayload(UUID uuid, boolean enable , List<ItemStack> items) implements CustomPayload {
    public static final Id<HopperMinecartDataPayload> ID = new CustomPayload.Id<>(MinecartVisualizer.HOPPER_MINECART_DATA_PACKET_ID);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static final PacketCodec<RegistryByteBuf, HopperMinecartDataPayload> CODEC = PacketCodec.of(
            (payload, buf) -> {
                buf.writeUuid(payload.uuid());
                buf.writeBoolean(payload.enable());
                for (int i = 0; i < 5; i++) {
                    ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, payload.items().get(i));
                }
            },
            buf -> {
                UUID uuid = buf.readUuid();
                boolean enable = buf.readBoolean();
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < 5; i++) {
                    items.add(ItemStack.OPTIONAL_PACKET_CODEC.decode(buf));
                }
                return new HopperMinecartDataPayload(uuid, enable, items);
            }
    );
}



