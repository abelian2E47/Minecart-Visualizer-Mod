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


@Mixin(AbstractMinecartEntity.class)
public abstract class MinecartCollisionMixin {

    @Unique private Vec3d minecartvisualizer$velocityBeforeMove;
    @Unique private Vec3d minecartvisualizer$pushVelocitySelf;
    @Unique private Vec3d minecartvisualizer$pushVelocityOther;

    @Inject(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"))
    private void minecartvisualizer$captureMoveStart(MovementType type, Vec3d movement, CallbackInfo ci) {
        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        if (self.getWorld().isClient()) return;

        this.minecartvisualizer$velocityBeforeMove = self.getVelocity();
    }

    @Inject(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V", at = @At("RETURN"))
    private void minecartvisualizer$reportMoveCollision(MovementType type, Vec3d movement, CallbackInfo ci) {
        Vec3d velocityBefore = this.minecartvisualizer$velocityBeforeMove;
        this.minecartvisualizer$velocityBeforeMove = null;

        if (velocityBefore == null) return;

        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        if (self.getWorld().isClient()) return;
        if (!self.horizontalCollision) return;

        MinecartCollisionReporter.reportBlockCollision(self, velocityBefore, self.getVelocity());
    }

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
