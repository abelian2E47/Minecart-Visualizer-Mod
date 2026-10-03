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
            //数据不新鲜时（矿车跑出服务端广播范围）不接管模型：否则"漏斗被锁"的外观会一直冻在那儿
            HopperMinecartDataPayload data = MinecartClientHandler.getFreshHopperMinecartData(this.getUuid());
            if (data != null && !data.enable()) {
                BlockState state = cir.getReturnValue();
                if (state != null) {
                    cir.setReturnValue(state.withIfExists(HopperBlock.ENABLED, false));
                }
            }
        }
    }
}