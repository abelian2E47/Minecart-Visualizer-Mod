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

//挤压监测
@Mixin(Entity.class)
public abstract class EntityPushAwayFromMixin {

    @Unique private Vec3d minecartvisualizer$pushVelocitySelf;
    @Unique private Vec3d minecartvisualizer$pushVelocityOther;

    @Inject(method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
    private void minecartvisualizer$capturePush(Entity entity, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof AbstractMinecartEntity) && !(entity instanceof AbstractMinecartEntity)) return;
        //1.21.1 里取世界的方法是 getWorld()（1.21.2 起改名为 getEntityWorld()）
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

        Entity self = (Entity) (Object) this;
        if (selfBefore != null && self instanceof AbstractMinecartEntity selfCart) {
            MinecartCollisionReporter.reportEntityCollision(selfCart, entity, selfBefore, self.getVelocity());
        }
        if (otherBefore != null && entity instanceof AbstractMinecartEntity otherCart) {
            MinecartCollisionReporter.reportEntityCollision(otherCart, self, otherBefore, otherCart.getVelocity());
        }
    }
}
