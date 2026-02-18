package com.minecartvisualizer.mixin;


import com.minecartvisualizer.HopperMinecartDataPayload;
import com.minecartvisualizer.TNTMinecartDataPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(TntMinecartEntity.class)
public abstract class TNTMinecartEntityMixin extends VehicleEntity {



    @Shadow public abstract int getFuseTicks();

    public TNTMinecartEntityMixin(EntityType<?> type, World world) {super(type, world);}

    @Inject(at = @At("TAIL"), method = "tick")
    public void sendTNTMinecartData(CallbackInfo ci) {
        if (!this.getWorld().isClient) {

            ServerWorld serverWorld = (ServerWorld) this.getWorld();

            UUID uuid = this.getUuid();
            int fuseTicks = this.getFuseTicks();
            Float damageWobbleStrength = this.getDamageWobbleStrength();

            TNTMinecartDataPayload payload = new TNTMinecartDataPayload(uuid, fuseTicks ,false , new Vec3d(114514,191981,0), damageWobbleStrength);
            serverWorld.getPlayers(player -> player.squaredDistanceTo(this) < 32 * 32)
                    .forEach(player -> {
                        if (ServerPlayNetworking.canSend(player, TNTMinecartDataPayload.ID)) {
                            ServerPlayNetworking.send(player, payload);
                        }
                    });
        }
    }

   @Inject(at = @At("TAIL"), method = "explode(Lnet/minecraft/entity/damage/DamageSource;D)V")
    public void sendExplosionData(DamageSource damageSource, double power, CallbackInfo ci){
        if (!this.getWorld().isClient) {

            ServerWorld serverWorld = (ServerWorld) this.getWorld();

            UUID uuid = this.getUuid();

            Vec3d explosionPos = this.getPos();

            TNTMinecartDataPayload payload = new TNTMinecartDataPayload(uuid, 0, true, explosionPos, 0.0f);
            serverWorld.getPlayers(player -> player.squaredDistanceTo(this) <64 * 64)
                    .forEach(player -> ServerPlayNetworking.send(player, payload));

        }
    }
}
