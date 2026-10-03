package com.minecartvisualizer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;


public final class MinecartDataSender {

    public static final double SEND_DISTANCE = 32.0;
    public static final double REMOVAL_SEND_DISTANCE = 256.0;

    private MinecartDataSender() {
    }

    public static void sendMinecart(AbstractMinecart cart, boolean removed) {
        if (cart.level().isClientSide()) return;

        ServerLevel serverWorld = (ServerLevel) cart.level();

        double xMovement = cart.xo - cart.getX();
        double yMovement = cart.yo - cart.getY();
        double zMovement = cart.zo - cart.getZ();
        double movement = new Vec3(xMovement, yMovement, zMovement).length();

        MinecartDataPayload payload = new MinecartDataPayload(
                cart.getUUID(),
                cart.position(),
                cart.getDeltaMovement(),
                movement,
                cart.getYRot(),
                cart.getId(),
                serverWorld.getGameTime(),
                removed
        );

        double distance = removed ? REMOVAL_SEND_DISTANCE : SEND_DISTANCE;
        sendToNearby(serverWorld, cart, distance, MinecartDataPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    public static void sendHopper(MinecartHopper cart) {
        if (cart.level().isClientSide()) return;

        ServerLevel serverWorld = (ServerLevel) cart.level();

        UUID uuid = cart.getUUID();
        boolean enable = cart.isEnabled();
        List<ItemStack> items = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            ItemStack stack = cart.getItem(i);
            items.add(stack == null ? ItemStack.EMPTY : stack);
        }

        HopperMinecartDataPayload payload = new HopperMinecartDataPayload(
                uuid, enable, items, findExtractionBlock(cart), findExtractionEntities(cart));

        sendToNearby(serverWorld, cart, SEND_DISTANCE, HopperMinecartDataPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    public static void sendTnt(MinecartTNT cart) {
        if (cart.level().isClientSide()) return;

        ServerLevel serverWorld = (ServerLevel) cart.level();

        TNTMinecartDataPayload payload = new TNTMinecartDataPayload(
                cart.getUUID(), cart.getFuse(), false, Vec3.ZERO, cart.getDamage());

        sendToNearby(serverWorld, cart, SEND_DISTANCE, TNTMinecartDataPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    public static void sendExplosion(MinecartTNT cart) {
        if (cart.level().isClientSide()) return;

        ServerLevel serverWorld = (ServerLevel) cart.level();

        TNTMinecartDataPayload payload = new TNTMinecartDataPayload(
                cart.getUUID(), 0, true, cart.position(), 0.0f);

        sendToNearby(serverWorld, cart, 64.0, TNTMinecartDataPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    public static void sendCollision(MinecartCollisionPayload payload, Entity source) {
        if (source.level().isClientSide()) return;

        ServerLevel serverWorld = (ServerLevel) source.level();
        sendToNearby(serverWorld, source, SEND_DISTANCE, MinecartCollisionPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    public static void sendRemoval(AbstractMinecart cart) {
        if (cart.level().isClientSide()) return;

        if (cart instanceof MinecartHopper hopper) {
            sendHopper(hopper);
        }
        sendMinecart(cart, true);
    }


    public static Optional<BlockPos> findExtractionBlock(MinecartHopper cart) {
        Level world = cart.level();
        BlockPos pos = BlockPos.containing(cart.getLevelX(), cart.getLevelY() + 1.0, cart.getLevelZ());
        BlockState state = world.getBlockState(pos);

        return hasBlockInventory(world, pos, state) ? Optional.of(pos) : Optional.empty();
    }

    public static List<AABB> findExtractionEntities(MinecartHopper cart) {
        Level world = cart.level();
        double x = cart.getLevelX();
        double y = cart.getLevelY() + 1.0;
        double z = cart.getLevelZ();
        AABB searchBox = new AABB(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5);

        List<AABB> boxes = new ArrayList<>();
        for (Entity entity : world.getEntities(cart, searchBox, EntitySelector.CONTAINER_ENTITY_SELECTOR)) {
            boxes.add(entity.getBoundingBox());
        }
        return boxes;
    }

    private static boolean hasBlockInventory(Level world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof WorldlyContainerHolder) {
            return true;
        }

        return state.hasBlockEntity() && world.getBlockEntity(pos) instanceof Container;
    }

    private static void sendToNearby(ServerLevel world, Entity source, double distance,
                                     CustomPacketPayload.Type<?> payloadId, Consumer<ServerPlayer> sender) {
        double squared = distance * distance;
        world.getPlayers(player -> player.distanceToSqr(source) < squared).forEach(player -> {
            if (ServerPlayNetworking.canSend(player, payloadId)) {
                sender.accept(player);
            }
        });
    }
}
