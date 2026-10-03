package com.minecartvisualizer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.InventoryProvider;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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

    public static void sendMinecart(AbstractMinecartEntity cart, boolean removed) {
        if (cart.getEntityWorld().isClient()) return;

        ServerWorld serverWorld = (ServerWorld) cart.getEntityWorld();

        double xMovement = cart.lastX - cart.getX();
        double yMovement = cart.lastY - cart.getY();
        double zMovement = cart.lastZ - cart.getZ();
        double movement = new Vec3d(xMovement, yMovement, zMovement).length();

        MinecartDataPayload payload = new MinecartDataPayload(
                cart.getUuid(),
                cart.getEntityPos(),
                cart.getVelocity(),
                movement,
                cart.getYaw(),
                cart.getId(),
                serverWorld.getTime(),
                removed
        );

        double distance = removed ? REMOVAL_SEND_DISTANCE : SEND_DISTANCE;
        sendToNearby(serverWorld, cart, distance, MinecartDataPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    public static void sendHopper(HopperMinecartEntity cart) {
        if (cart.getEntityWorld().isClient()) return;

        ServerWorld serverWorld = (ServerWorld) cart.getEntityWorld();

        UUID uuid = cart.getUuid();
        boolean enable = cart.isEnabled();
        List<ItemStack> items = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            ItemStack stack = cart.getStack(i);
            items.add(stack == null ? ItemStack.EMPTY : stack);
        }

        HopperMinecartDataPayload payload = new HopperMinecartDataPayload(
                uuid, enable, items, findExtractionBlock(cart), findExtractionEntities(cart));

        sendToNearby(serverWorld, cart, SEND_DISTANCE, HopperMinecartDataPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    public static void sendTnt(TntMinecartEntity cart) {
        if (cart.getEntityWorld().isClient()) return;

        ServerWorld serverWorld = (ServerWorld) cart.getEntityWorld();

        TNTMinecartDataPayload payload = new TNTMinecartDataPayload(
                cart.getUuid(), cart.getFuseTicks(), false, Vec3d.ZERO, cart.getDamageWobbleStrength());

        sendToNearby(serverWorld, cart, SEND_DISTANCE, TNTMinecartDataPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    public static void sendExplosion(TntMinecartEntity cart) {
        if (cart.getEntityWorld().isClient()) return;

        ServerWorld serverWorld = (ServerWorld) cart.getEntityWorld();

        TNTMinecartDataPayload payload = new TNTMinecartDataPayload(
                cart.getUuid(), 0, true, cart.getEntityPos(), 0.0f);

        sendToNearby(serverWorld, cart, 64.0, TNTMinecartDataPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    public static void sendCollision(MinecartCollisionPayload payload, Entity source) {
        if (source.getEntityWorld().isClient()) return;

        ServerWorld serverWorld = (ServerWorld) source.getEntityWorld();
        sendToNearby(serverWorld, source, SEND_DISTANCE, MinecartCollisionPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    public static void sendRemoval(AbstractMinecartEntity cart) {
        if (cart.getEntityWorld().isClient()) return;

        if (cart instanceof HopperMinecartEntity hopper) {
            sendHopper(hopper);
        }
        sendMinecart(cart, true);
    }


    public static Optional<BlockPos> findExtractionBlock(HopperMinecartEntity cart) {
        World world = cart.getEntityWorld();
        BlockPos pos = BlockPos.ofFloored(cart.getHopperX(), cart.getHopperY() + 1.0, cart.getHopperZ());
        BlockState state = world.getBlockState(pos);

        return hasBlockInventory(world, pos, state) ? Optional.of(pos) : Optional.empty();
    }

    public static List<Box> findExtractionEntities(HopperMinecartEntity cart) {
        World world = cart.getEntityWorld();
        double x = cart.getHopperX();
        double y = cart.getHopperY() + 1.0;
        double z = cart.getHopperZ();
        Box searchBox = new Box(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5);

        List<Box> boxes = new ArrayList<>();
        for (Entity entity : world.getOtherEntities(cart, searchBox, EntityPredicates.VALID_INVENTORIES)) {
            boxes.add(entity.getBoundingBox());
        }
        return boxes;
    }

    private static boolean hasBlockInventory(World world, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof InventoryProvider) {
            return true;
        }

        return state.hasBlockEntity() && world.getBlockEntity(pos) instanceof Inventory;
    }

    private static void sendToNearby(ServerWorld world, Entity source, double distance,
                                     CustomPayload.Id<?> payloadId, Consumer<ServerPlayerEntity> sender) {
        double squared = distance * distance;
        world.getPlayers(player -> player.squaredDistanceTo(source) < squared).forEach(player -> {
            if (ServerPlayNetworking.canSend(player, payloadId)) {
                sender.accept(player);
            }
        });
    }
}
