package com.minecartvisualizer.mixin;

import com.minecartvisualizer.MinecartDataSender;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecartHopper.class)
public abstract class HopperMinecartEntityMixin extends AbstractMinecartContainer {

    protected HopperMinecartEntityMixin(EntityType<?> type, Level world) {super(type, world);}

    @Inject(at = @At("TAIL"), method = "tick")
    public void sendHopperMinecartData(CallbackInfo ci) {
        MinecartDataSender.sendHopper((MinecartHopper) (Object) this);
    }
}
