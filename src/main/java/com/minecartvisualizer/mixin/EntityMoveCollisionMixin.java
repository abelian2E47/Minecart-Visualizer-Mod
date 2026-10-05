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
