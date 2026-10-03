package com.minecartvisualizer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MinecartCollisionReporter {

    private static final double MIN_DELTA = 1.0E-4;

    private MinecartCollisionReporter() {
    }

//方块碰撞
    public static void reportBlockCollision(AbstractMinecart cart,
                                            Vec3 velocityBefore, Vec3 velocityAfter) {
        if (cart.level().isClientSide()) return;

        Vec3 delta = blockedAxisDelta(velocityBefore, velocityAfter);
        if (delta.lengthSqr() < MIN_DELTA * MIN_DELTA) return;

        Vec3 probeDirection = delta.normalize().reverse();

        Level world = cart.level();
        BlockPos hit = findBlockedBlock(world, cart.getBoundingBox(), probeDirection);

        String targetId = "";
        Vec3 targetPos = cart.position().add(probeDirection);
        if (hit != null) {
            BlockState state = world.getBlockState(hit);
            targetId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            targetPos = Vec3.atCenterOf(hit);
        }

        send(cart, cart.position(), delta, velocityBefore, velocityAfter,
                true, targetId, "", targetPos);
    }

    private static Vec3 blockedAxisDelta(Vec3 before, Vec3 after) {
        double x = isZeroed(before.x, after.x) ? -before.x : 0.0;
        double z = isZeroed(before.z, after.z) ? -before.z : 0.0;
        return new Vec3(x, 0.0, z);
    }

    private static boolean isZeroed(double before, double after) {
        return after == 0.0 && Math.abs(before) > MIN_DELTA;
    }

    public static void reportEntityCollision(AbstractMinecart cart, Entity other,
                                             Vec3 velocityBefore, Vec3 velocityAfter) {
        if (cart.level().isClientSide()) return;

        Vec3 delta = velocityAfter.subtract(velocityBefore);
        if (delta.lengthSqr() < MIN_DELTA * MIN_DELTA) return;

        String targetId = BuiltInRegistries.ENTITY_TYPE.getKey(other.getType()).toString();
        String customName = "";
        if (other.hasCustomName() && other.getCustomName() != null) {
            customName = other.getCustomName().getString();
        }

        send(cart, cart.position(), delta, velocityBefore, velocityAfter,
                false, targetId, customName, other.position());
    }

    private static void send(AbstractMinecart cart, Vec3 pos, Vec3 delta,
                             Vec3 velocityBefore, Vec3 velocityAfter,
                             boolean blockTarget, String targetId, String targetCustomName,
                             Vec3 targetPos) {
        long serverTime = ((ServerLevel) cart.level()).getGameTime();

        MinecartCollisionPayload payload = new MinecartCollisionPayload(
                cart.getUUID(), pos, delta,
                velocityBefore.length(), velocityAfter.length(),
                blockTarget, targetId, targetCustomName, targetPos, serverTime);

        MinecartDataSender.sendCollision(payload, cart);
    }

    private static BlockPos findBlockedBlock(Level world, AABB box, Vec3 direction) {
        double ax = Math.abs(direction.x);
        double ay = Math.abs(direction.y);
        double az = Math.abs(direction.z);

        Direction.Axis axis;
        double sign;
        if (ax >= ay && ax >= az) {
            axis = Direction.Axis.X;
            sign = Math.signum(direction.x);
        } else if (ay >= az) {
            axis = Direction.Axis.Y;
            sign = Math.signum(direction.y);
        } else {
            axis = Direction.Axis.Z;
            sign = Math.signum(direction.z);
        }

        if (sign == 0.0) return null;

        double eps = 1.0E-3;
        double depth = 0.05;
        AABB probe = switch (axis) {
            case X -> sign > 0
                    ? new AABB(box.maxX + eps, box.minY, box.minZ, box.maxX + eps + depth, box.maxY, box.maxZ)
                    : new AABB(box.minX - eps - depth, box.minY, box.minZ, box.minX - eps, box.maxY, box.maxZ);
            case Y -> sign > 0
                    ? new AABB(box.minX, box.maxY + eps, box.minZ, box.maxX, box.maxY + eps + depth, box.maxZ)
                    : new AABB(box.minX, box.minY - eps - depth, box.minZ, box.maxX, box.minY - eps, box.maxZ);
            case Z -> sign > 0
                    ? new AABB(box.minX, box.minY, box.maxZ + eps, box.maxX, box.maxY, box.maxZ + eps + depth)
                    : new AABB(box.minX, box.minY, box.minZ - eps - depth, box.maxX, box.maxY, box.minZ - eps);
        };

        for (BlockPos pos : BlockPos.betweenClosed(probe)) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getCollisionShape(world, pos).isEmpty()) continue;
            //BlockPos.iterate 复用同一个可变对象，必须复制出来
            return new BlockPos(pos);
        }

        return null;
    }
}
