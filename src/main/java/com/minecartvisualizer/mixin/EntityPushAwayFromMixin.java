package com.minecartvisualizer.mixin;

import com.minecartvisualizer.MinecartCollisionReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//挤压监测
@Mixin(Entity.class)
public abstract class EntityPushAwayFromMixin {

    @Unique private Vec3 minecartvisualizer$pushVelocitySelf;
    @Unique private Vec3 minecartvisualizer$pushVelocityOther;

    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"))
    private void minecartvisualizer$capturePush(Entity entity, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof AbstractMinecart) && !(entity instanceof AbstractMinecart)) return;
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

        Entity self = (Entity) (Object) this;
        if (selfBefore != null && self instanceof AbstractMinecart selfCart) {
            MinecartCollisionReporter.reportEntityCollision(selfCart, entity, selfBefore, self.getDeltaMovement());
        }
        if (otherBefore != null && entity instanceof AbstractMinecart otherCart) {
            MinecartCollisionReporter.reportEntityCollision(otherCart, self, otherBefore, otherCart.getDeltaMovement());
        }
    }
}
