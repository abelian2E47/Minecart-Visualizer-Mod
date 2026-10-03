package com.minecartvisualizer.mixin;

import com.minecartvisualizer.MinecartDataSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 矿车销毁的权威通知。
 *
 * <p>原版 {@code Entity#remove(RemovalReason)} 在服务端是所有移除路径的唯一出口，
 * 这里只在"真正被销毁"的两种原因（{@code KILLED} / {@code DISCARDED}）下补发最后一份数据，
 * 区块卸载（{@code UNLOADED_TO_CHUNK} 等）与跨维度搬迁不算销毁。</p>
 *
 * <p>客户端据此判定矿车被摧毁，不再用"客户端实体不见了"来推断——
 * 那会把走出视距、区块未加载、玩家离开的情况误报成矿车被摧毁。</p>
 */
@Mixin(Entity.class)
public abstract class EntityRemovalMixin {

    @Inject(method = "remove(Lnet/minecraft/entity/Entity$RemovalReason;)V", at = @At("HEAD"))
    private void minecartvisualizer$sendRemovalNotice(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.getEntityWorld().isClient()) return;
        if (!(self instanceof AbstractMinecartEntity cart)) return;

        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            MinecartDataSender.sendRemoval(cart);
        }
    }
}
