package com.minecartvisualizer;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.InventoryProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.predicate.entity.EntityPredicates;
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

/**
 * 服务端下发矿车数据的统一出口。
 *
 * <p>客户端显示的一切矿车信息都应当来自这里：位置、速度、朝向、物品栏、是否可用、
 * 服务端时间、销毁事件以及"这一 tick 真正会被吸取的对象"。客户端只负责画，
 * 不再自行推算，从而避免因客户端状态滞后或推算口径不同而显示不准确。</p>
 */
public final class MinecartDataSender {

    /** 常规状态的下发半径，与 {@code infoRenderDistance} 配置的上限一致（超出即不显示）。 */
    public static final double SEND_DISTANCE = 32.0;

    /** 销毁通知的下发半径：一次性小包，发给可能仍在追踪该矿车的玩家，避免漏报。 */
    public static final double REMOVAL_SEND_DISTANCE = 256.0;

    private MinecartDataSender() {
    }

    /** 下发一次矿车状态（{@code removed=true} 表示这是矿车被销毁前的最后一份数据）。 */
    public static void sendMinecart(AbstractMinecartEntity cart, boolean removed) {
        if (cart.getWorld().isClient()) return;

        ServerWorld serverWorld = (ServerWorld) cart.getWorld();

        double xMovement = cart.lastX - cart.getX();
        double yMovement = cart.lastY - cart.getY();
        double zMovement = cart.lastZ - cart.getZ();
        double movement = new Vec3d(xMovement, yMovement, zMovement).length();

        MinecartDataPayload payload = new MinecartDataPayload(
                cart.getUuid(),
                cart.getPos(),
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

    /** 下发一次漏斗矿车状态（物品栏、是否可用、服务端算出的吸取目标）。 */
    public static void sendHopper(HopperMinecartEntity cart) {
        if (cart.getWorld().isClient()) return;

        ServerWorld serverWorld = (ServerWorld) cart.getWorld();

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

    /** 下发一次 TNT 矿车状态（引信、抖动强度）。 */
    public static void sendTnt(TntMinecartEntity cart) {
        if (cart.getWorld().isClient()) return;

        ServerWorld serverWorld = (ServerWorld) cart.getWorld();

        TNTMinecartDataPayload payload = new TNTMinecartDataPayload(
                cart.getUuid(), cart.getFuseTicks(), false, Vec3d.ZERO, cart.getDamageWobbleStrength());

        sendToNearby(serverWorld, cart, SEND_DISTANCE, TNTMinecartDataPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    /** 一次性爆炸事件：发给更远的玩家（原版爆炸可见范围更大）。 */
    public static void sendExplosion(TntMinecartEntity cart) {
        if (cart.getWorld().isClient()) return;

        ServerWorld serverWorld = (ServerWorld) cart.getWorld();

        TNTMinecartDataPayload payload = new TNTMinecartDataPayload(
                cart.getUuid(), 0, true, cart.getPos(), 0.0f);

        sendToNearby(serverWorld, cart, 64.0, TNTMinecartDataPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    /** 下发一次"矿车被挤压"事件（碰撞前后速度差由服务端算好）。 */
    public static void sendCollision(MinecartCollisionPayload payload, Entity source) {
        if (source.getWorld().isClient()) return;

        ServerWorld serverWorld = (ServerWorld) source.getWorld();
        sendToNearby(serverWorld, source, SEND_DISTANCE, MinecartCollisionPayload.ID,
                player -> ServerPlayNetworking.send(player, payload));
    }

    /** 矿车被真正销毁时补发最后一份数据（带 {@code removed} 标记）。 */
    public static void sendRemoval(AbstractMinecartEntity cart) {
        if (cart.getWorld().isClient()) return;

        //先发漏斗矿车快照（销毁瞬间的最终物品栏），再发带 removed 标记的矿车数据，
        //这样客户端处理 removed 时手上已经有权威的最终物品栏
        if (cart instanceof HopperMinecartEntity hopper) {
            sendHopper(hopper);
        }
        sendMinecart(cart, true);
    }

    /**
     * 按原版 {@code HopperBlockEntity#extract} 的口径找会被吸取的容器方块：
     * 位置为 {@code BlockPos.ofFloored(hopperX, hopperY + 1.0, hopperZ)}，
     * 判定与原版 {@code getBlockInventoryAt} 一致。
     */
    public static Optional<BlockPos> findExtractionBlock(HopperMinecartEntity cart) {
        World world = cart.getWorld();
        BlockPos pos = BlockPos.ofFloored(cart.getHopperX(), cart.getHopperY() + 1.0, cart.getHopperZ());
        BlockState state = world.getBlockState(pos);

        return hasBlockInventory(world, pos, state) ? Optional.of(pos) : Optional.empty();
    }

    /**
     * 按原版 {@code HopperBlockEntity#getEntityInventoryAt} 的口径找会被吸取的实体容器：
     * 以 {@code (hopperX, hopperY + 1.0, hopperZ)} 为中心、半径 0.5 的立方体，
     * 谓词为 {@code EntityPredicates.VALID_INVENTORIES}。
     */
    public static List<Box> findExtractionEntities(HopperMinecartEntity cart) {
        World world = cart.getWorld();
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

    /** 原版 {@code HopperBlockEntity#getBlockInventoryAt}：方块自己提供容器，或方块实体实现了 Inventory。 */
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
