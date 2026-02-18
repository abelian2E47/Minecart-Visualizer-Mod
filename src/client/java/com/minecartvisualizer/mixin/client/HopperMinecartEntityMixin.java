package com.minecartvisualizer.mixin.client;

import com.minecartvisualizer.HopperMinecartDataPayload;
import com.minecartvisualizer.MinecartClientHandler;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.HopperBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;



@Mixin(HopperMinecartEntity.class)

public abstract class HopperMinecartEntityMixin extends Entity {

    public HopperMinecartEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "getDefaultContainedBlock", at = @At("RETURN"), cancellable = true)
    private void displayLockedHopperWhenLocked(CallbackInfoReturnable<BlockState> cir) {
        if (MinecartVisualizerConfig.getInstance().enableHopperMinecartEnableDisplay){
            HopperMinecartDataPayload data = MinecartClientHandler.getHopperMinecartData(this.getUuid());
            if (data != null && !data.enable()) {
                BlockState state = cir.getReturnValue();
                if (state != null) {
                    cir.setReturnValue(state.withIfExists(HopperBlock.ENABLED, false));
                }
            }
        }
    }
}