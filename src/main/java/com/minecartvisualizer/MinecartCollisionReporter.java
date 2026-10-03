package com.minecartvisualizer;

import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class MinecartCollisionReporter {

    private static final double MIN_DELTA = 1.0E-4;

    private MinecartCollisionReporter() {
    }

//方块碰撞
    public static void reportBlockCollision(AbstractMinecartEntity cart,
                                            Vec3d velocityBefore, Vec3d velocityAfter) {
        //1.21.1 里取世界的方法是 getWorld()（1.21.2 起改名为 getEntityWorld()）
        if (cart.getWorld().isClient()) return;

        Vec3d delta = blockedAxisDelta(velocityBefore, velocityAfter);
        if (delta.lengthSquared() < MIN_DELTA * MIN_DELTA) return;

        Vec3d probeDirection = delta.normalize().negate();

        World world = cart.getWorld();
        BlockPos hit = findBlockedBlock(world, cart.getBoundingBox(), probeDirection);

        String targetId = "";
        //1.21.1 里取坐标的方法是 getPos()（1.21.2 起改名为 getEntityPos()）
        Vec3d targetPos = cart.getPos().add(probeDirection);
        if (hit != null) {
            BlockState state = world.getBlockState(hit);
            targetId = Registries.BLOCK.getId(state.getBlock()).toString();
            targetPos = Vec3d.ofCenter(hit);
        }

        send(cart, cart.getPos(), delta, velocityBefore, velocityAfter,
                true, targetId, "", targetPos);
    }

    private static Vec3d blockedAxisDelta(Vec3d before, Vec3d after) {
        double x = isZeroed(before.x, after.x) ? -before.x : 0.0;
        double z = isZeroed(before.z, after.z) ? -before.z : 0.0;
        return new Vec3d(x, 0.0, z);
    }

    private static boolean isZeroed(double before, double after) {
        return after == 0.0 && Math.abs(before) > MIN_DELTA;
    }

    public static void reportEntityCollision(AbstractMinecartEntity cart, Entity other,
                                             Vec3d velocityBefore, Vec3d velocityAfter) {
        if (cart.getWorld().isClient()) return;

        Vec3d delta = velocityAfter.subtract(velocityBefore);
        if (delta.lengthSquared() < MIN_DELTA * MIN_DELTA) return;

        String targetId = Registries.ENTITY_TYPE.getId(other.getType()).toString();
        String customName = "";
        if (other.hasCustomName() && other.getCustomName() != null) {
            customName = other.getCustomName().getString();
        }

        send(cart, cart.getPos(), delta, velocityBefore, velocityAfter,
                false, targetId, customName, other.getPos());
    }

    private static void send(AbstractMinecartEntity cart, Vec3d pos, Vec3d delta,
                             Vec3d velocityBefore, Vec3d velocityAfter,
                             boolean blockTarget, String targetId, String targetCustomName,
                             Vec3d targetPos) {
        long serverTime = ((ServerWorld) cart.getWorld()).getTime();

        MinecartCollisionPayload payload = new MinecartCollisionPayload(
                cart.getUuid(), pos, delta,
                velocityBefore.length(), velocityAfter.length(),
                blockTarget, targetId, targetCustomName, targetPos, serverTime);

        MinecartDataSender.sendCollision(payload, cart);
    }

    private static BlockPos findBlockedBlock(World world, Box box, Vec3d direction) {
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
        Box probe = switch (axis) {
            case X -> sign > 0
                    ? new Box(box.maxX + eps, box.minY, box.minZ, box.maxX + eps + depth, box.maxY, box.maxZ)
                    : new Box(box.minX - eps - depth, box.minY, box.minZ, box.minX - eps, box.maxY, box.maxZ);
            case Y -> sign > 0
                    ? new Box(box.minX, box.maxY + eps, box.minZ, box.maxX, box.maxY + eps + depth, box.maxZ)
                    : new Box(box.minX, box.minY - eps - depth, box.minZ, box.maxX, box.minY - eps, box.maxZ);
            case Z -> sign > 0
                    ? new Box(box.minX, box.minY, box.maxZ + eps, box.maxX, box.maxY, box.maxZ + eps + depth)
                    : new Box(box.minX, box.minY, box.minZ - eps - depth, box.maxX, box.maxY, box.minZ - eps);
        };

        //1.21.1 没有 BlockPos.iterate(Box)（1.21.2 起才有），这里用 iterate(BlockPos, BlockPos) 等价替代：
        //两端都按 floor 取整，遍历范围与 iterate(Box) / stream(Box) 完全一致
        for (BlockPos pos : BlockPos.iterate(
                BlockPos.ofFloored(probe.minX, probe.minY, probe.minZ),
                BlockPos.ofFloored(probe.maxX, probe.maxY, probe.maxZ))) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;
            if (state.getCollisionShape(world, pos).isEmpty()) continue;
            //BlockPos.iterate 复用同一个可变对象，必须复制出来
            return new BlockPos(pos);
        }

        return null;
    }
}
