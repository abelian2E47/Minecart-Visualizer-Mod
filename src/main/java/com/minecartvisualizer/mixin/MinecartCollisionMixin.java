package com.minecartvisualizer.mixin;

import com.minecartvisualizer.MinecartCollisionReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(AbstractMinecart.class)
public abstract class MinecartCollisionMixin {

    @Unique private Vec3 minecartvisualizer$velocityBeforeMove;
    @Unique private Vec3 minecartvisualizer$pushVelocitySelf;
    @Unique private Vec3 minecartvisualizer$pushVelocityOther;

    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"))
    private void minecartvisualizer$captureMoveStart(MoverType type, Vec3 movement, CallbackInfo ci) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        if (self.level().isClientSide()) return;

        this.minecartvisualizer$velocityBeforeMove = self.getDeltaMovement();
    }

    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", at = @At("RETURN"))
    private void minecartvisualizer$reportMoveCollision(MoverType type, Vec3 movement, CallbackInfo ci) {
        Vec3 velocityBefore = this.minecartvisualizer$velocityBeforeMove;
        this.minecartvisualizer$velocityBeforeMove = null;

        if (velocityBefore == null) return;

        AbstractMinecart self = (AbstractMinecart) (Object) this;
        if (self.level().isClientSide()) return;
        if (!self.horizontalCollision) return;

        MinecartCollisionReporter.reportBlockCollision(self, velocityBefore, self.getDeltaMovement());
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void minecartvisualizer$capturePush(Entity entity, CallbackInfo ci) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        if (self.level().isClientSide()) return;

        this.minecartvisualizer$pushVelocitySelf = self.getDeltaMovement();
        this.minecartvisualizer$pushVelocityOther = entity.getDeltaMovement();
    }

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("RETURN"))
    private void minecartvisualizer$reportPush(Entity entity, CallbackInfo ci) {
        Vec3 selfBefore = this.minecartvisualizer$pushVelocitySelf;
        Vec3 otherBefore = this.minecartvisualizer$pushVelocityOther;
        this.minecartvisualizer$pushVelocitySelf = null;
        this.minecartvisualizer$pushVelocityOther = null;

        if (selfBefore == null && otherBefore == null) return;

        AbstractMinecart self = (AbstractMinecart) (Object) this;
        if (selfBefore != null) {
            MinecartCollisionReporter.reportEntityCollision(self, entity, selfBefore, self.getDeltaMovement());
        }

        if (otherBefore != null && entity instanceof AbstractMinecart other) {
            MinecartCollisionReporter.reportEntityCollision(other, self, otherBefore, other.getDeltaMovement());
        }
    }
}
