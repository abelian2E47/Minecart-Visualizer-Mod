package com.minecartvisualizer.mixin;

import com.minecartvisualizer.MinecartDataSender;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HopperMinecartEntity.class)
public abstract class HopperMinecartEntityMixin extends StorageMinecartEntity {

    protected HopperMinecartEntityMixin(EntityType<?> type, World world) {super(type, world);}

    @Inject(at = @At("TAIL"), method = "tick")
    public void sendHopperMinecartData(CallbackInfo ci) {
        MinecartDataSender.sendHopper((HopperMinecartEntity) (Object) this);
    }
}
