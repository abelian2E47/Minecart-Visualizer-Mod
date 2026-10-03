package com.minecartvisualizer.mixin;

import com.minecartvisualizer.MinecartDataSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartEntityMixin extends Entity {


    protected AbstractMinecartEntityMixin(EntityType<?> entityType, World world) {
        super(entityType, world);
    }

    @Inject(at = @At("TAIL"), method = "tick")
    public void sendMinecartData(CallbackInfo ci) {
        //统一走 MinecartDataSender：里面带 canSend 校验，并补上服务端时间戳
        MinecartDataSender.sendMinecart((AbstractMinecartEntity) (Object) this, false);
    }
}
