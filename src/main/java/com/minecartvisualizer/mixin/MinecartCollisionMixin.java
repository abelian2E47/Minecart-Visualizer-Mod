package com.minecartvisualizer.mixin;

import com.minecartvisualizer.MinecartCollisionReporter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 实体碰撞（推挤）的监测点。
 *
 * <p>1.21.1 的 {@code AbstractMinecartEntity} 同样覆写了 {@code pushAwayFrom(Entity)}，
 * 所以这一半可以像 1.21.11 一样直接挂在矿车类上。</p>
 *
 * <p>注意：1.21.11 里 {@code move(MovementType, Vec3d)} 的两个注入点原本也在这个类里，
 * 但 1.21.1 的 {@code AbstractMinecartEntity} 没有覆写 {@code move}（只继承 {@code Entity} 的），
 * 挂在这里会解析不到方法，已改到 {@link EntityMoveCollisionMixin}。</p>
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartCollisionMixin {

    @Unique private Vec3d minecartvisualizer$pushVelocitySelf;
    @Unique private Vec3d minecartvisualizer$pushVelocityOther;

    @Inject(method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
    private void minecartvisualizer$capturePush(Entity entity, CallbackInfo ci) {
        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        if (self.getWorld().isClient()) return;

        this.minecartvisualizer$pushVelocitySelf = self.getVelocity();
        this.minecartvisualizer$pushVelocityOther = entity.getVelocity();
    }

    @Inject(method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V", at = @At("RETURN"))
    private void minecartvisualizer$reportPush(Entity entity, CallbackInfo ci) {
        Vec3d selfBefore = this.minecartvisualizer$pushVelocitySelf;
        Vec3d otherBefore = this.minecartvisualizer$pushVelocityOther;
        this.minecartvisualizer$pushVelocitySelf = null;
        this.minecartvisualizer$pushVelocityOther = null;

        if (selfBefore == null && otherBefore == null) return;

        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        if (selfBefore != null) {
            MinecartCollisionReporter.reportEntityCollision(self, entity, selfBefore, self.getVelocity());
        }

        if (otherBefore != null && entity instanceof AbstractMinecartEntity other) {
            MinecartCollisionReporter.reportEntityCollision(other, self, otherBefore, other.getVelocity());
        }
    }
}
