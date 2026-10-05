package com.minecartvisualizer.mixin;


import com.minecartvisualizer.MinecartDataSender;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//1.20.1 里 TntMinecartEntity 的父类是 AbstractMinecartEntity（VehicleEntity 是 1.20.2 才抽出来的）
@Mixin(TntMinecartEntity.class)
public abstract class TNTMinecartEntityMixin extends AbstractMinecartEntity {

    public TNTMinecartEntityMixin(EntityType<?> type, World world) {super(type, world);}

    @Inject(at = @At("TAIL"), method = "tick")
    public void sendTNTMinecartData(CallbackInfo ci) {
        MinecartDataSender.sendTnt((TntMinecartEntity) (Object) this);
    }

   @Inject(at = @At("TAIL"), method = "explode(Lnet/minecraft/entity/damage/DamageSource;D)V")
    public void sendExplosionData(DamageSource damageSource, double power, CallbackInfo ci){
        //爆炸包也走统一出口，顺带补上 canSend 校验
        MinecartDataSender.sendExplosion((TntMinecartEntity) (Object) this);
    }
}
