package com.minecartvisualizer.mixin.client;

import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.TrackerColor;
import com.minecartvisualizer.tracker.TrackersManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin{
    @Unique
    Entity glowingEntity = (Entity) (Object) this;

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void glowingTracker(CallbackInfoReturnable<Boolean> ci) {
        var config = MinecartVisualizerConfig.getInstance();
        if (config.enableMinecartVisualization && config.glowingTrackingMinecart) {
            if(TrackersManager.containsTracker(glowingEntity.getUuid())){
                ci.setReturnValue(true);
            }
        }
    }

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void getTrackerTeamColor(CallbackInfoReturnable<Integer> ci) {
        var config = MinecartVisualizerConfig.getInstance();
        if (config.enableMinecartVisualization && config.glowingTrackingMinecart) {
            TrackerColor color = TrackersManager.getColorByUuid(glowingEntity.getUuid());
            if(glowingEntity != null){
                if (color != null) {
                    ci.setReturnValue(color.getHex());
                }
            }
        }
    }
}
