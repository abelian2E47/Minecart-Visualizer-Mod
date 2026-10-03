package com.minecartvisualizer.mixin;

import com.minecartvisualizer.MinecartDataSender;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecart.class)
public abstract class AbstractMinecartEntityMixin extends Entity {


    protected AbstractMinecartEntityMixin(EntityType<?> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(at = @At("TAIL"), method = "tick")
    public void sendMinecartData(CallbackInfo ci) {
        MinecartDataSender.sendMinecart((AbstractMinecart) (Object) this, false);
    }
}
