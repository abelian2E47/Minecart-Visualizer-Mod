package com.minecartvisualizer.mixin;


import com.minecartvisualizer.MinecartDataSender;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecartTNT.class)
public abstract class TNTMinecartEntityMixin extends VehicleEntity {


    public TNTMinecartEntityMixin(EntityType<?> type, Level world) {super(type, world);}

    @Inject(at = @At("TAIL"), method = "tick")
    public void sendTNTMinecartData(CallbackInfo ci) {
        MinecartDataSender.sendTnt((MinecartTNT) (Object) this);
    }

    @Inject(at = @At("TAIL"), method = "explode(Lnet/minecraft/world/damagesource/DamageSource;D)V")
    public void sendExplosionData(DamageSource damageSource, double power, CallbackInfo ci) {
        MinecartDataSender.sendExplosion((MinecartTNT) (Object) this);
    }
}
