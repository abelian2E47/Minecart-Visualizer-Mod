package com.minecartvisualizer.mixin;

import com.minecartvisualizer.MinecartCollisionReporter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 方块碰撞（矿车被方块挤住）的监测点。
 *
 * <p>1.21.11 的 {@code AbstractMinecartEntity} 自己覆写了 {@code move(MovementType, Vec3d)}，
 * 所以在那边可以直接挂在矿车类上；1.21.1 的 {@code AbstractMinecartEntity} 没有覆写，
 * 矿车走的是继承自 {@code Entity} 的 {@code move}。挂在矿车类上既解析不到方法
 * （Loom 无法重映射继承方法，会留下未重映射的 "move"，正式环境注入失败），
 * 语义也不对，因此这里改为挂在 {@code Entity} 上，并用 instanceof 限定只观察矿车，
 * 写法与 {@link EntityPushAwayFromMixin} 保持一致。</p>
 */
@Mixin(Entity.class)
public abstract class EntityMoveCollisionMixin {

    @Unique private Vec3d minecartvisualizer$velocityBeforeMove;

    @Inject(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"))
    private void minecartvisualizer$captureMoveStart(MovementType type, Vec3d movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof AbstractMinecartEntity)) return;
        //1.21.1 里取世界的方法是 getWorld()（1.21.2 起改名为 getEntityWorld()）
        if (self.getWorld().isClient()) return;

        this.minecartvisualizer$velocityBeforeMove = self.getVelocity();
    }

    @Inject(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V", at = @At("RETURN"))
    private void minecartvisualizer$reportMoveCollision(MovementType type, Vec3d movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof AbstractMinecartEntity selfCart)) return;

        Vec3d velocityBefore = this.minecartvisualizer$velocityBeforeMove;
        this.minecartvisualizer$velocityBeforeMove = null;

        if (velocityBefore == null) return;
        if (selfCart.getWorld().isClient()) return;
        if (!selfCart.horizontalCollision) return;

        MinecartCollisionReporter.reportBlockCollision(selfCart, velocityBefore, selfCart.getVelocity());
    }
}
