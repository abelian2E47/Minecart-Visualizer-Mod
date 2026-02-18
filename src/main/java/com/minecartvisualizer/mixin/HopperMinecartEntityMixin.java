package com.minecartvisualizer.mixin;

import com.minecartvisualizer.HopperMinecartDataPayload;
import com.minecartvisualizer.MinecartDataPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mixin(HopperMinecartEntity.class)
public abstract class HopperMinecartEntityMixin extends StorageMinecartEntity {

    @Shadow public abstract boolean isEnabled();

    protected HopperMinecartEntityMixin(EntityType<?> type, World world) {super(type, world);}

    @Inject(at = @At("TAIL"), method = "tick")
    public void sendHopperMinecartData(CallbackInfo ci) {
        if (!this.getWorld().isClient) {

            ServerWorld serverWorld = (ServerWorld) this.getWorld();

            UUID uuid = this.getUuid();
            boolean enable = this.isEnabled();
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                if (this.getStack(i) == null){
                    items.add((ItemStack.EMPTY));
                }else {
                    items.add(this.getStack(i));
                }
            }
            HopperMinecartDataPayload payload = new HopperMinecartDataPayload(uuid,enable,items);
            serverWorld.getPlayers(player -> player.squaredDistanceTo(this) < 32 * 32)
                    .forEach(player -> {
                        if (ServerPlayNetworking.canSend(player, HopperMinecartDataPayload.ID)) {
                            ServerPlayNetworking.send(player, payload);
                        }
                    });
        }
    }
}
