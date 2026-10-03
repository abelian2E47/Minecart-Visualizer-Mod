package com.minecartvisualizer;

import com.minecartvisualizer.command.MinecartVisualizerCommands;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.config.MinecartVisualizerConfigScreen;
import com.minecartvisualizer.tracker.TrackerColor;
import com.minecartvisualizer.tracker.TrackersManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import com.minecartvisualizer.tracker.TrackerPointsManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.*;


public class MinecartVisualizerClient implements ClientModInitializer {

	public static KeyMapping mainConfigKey;
	public static KeyMapping subConfigKey;
	public static UUID uuid;

	public void onInitializeClient() {
		MinecartVisualizerConfig.HANDLER.load();

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			MinecartVisualizerConfig.HANDLER.save();
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MinecartClientHandler.clearAll();
			TrackersManager.clearAll();
			TrackerPointsManager.getInstance().clearAllPoints();
		});

		MinecartClientHandler.register();
		MinecartVisualizerCommands.registerCommands();

		mainConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.minecartvisualizer.config_main",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_C,
				KeyMapping.Category.DEBUG
		));

		subConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.minecartvisualizer.config_sub",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_V,
				KeyMapping.Category.DEBUG
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;

			InputConstants.Key mainKey = KeyMappingHelper.getBoundKeyOf(mainConfigKey);
			InputConstants.Key subKey = KeyMappingHelper.getBoundKeyOf(subConfigKey);

			if (mainKey.equals(InputConstants.UNKNOWN)) return;

			boolean isSubKeyNone = subKey.equals(InputConstants.UNKNOWN);

			if (isSubKeyNone) {
				while (mainConfigKey.consumeClick()) {
					client.gui.setScreen(MinecartVisualizerConfigScreen.create(client.gui.screen()));
				}
			} else {
				while (subConfigKey.consumeClick()) {
					if (InputConstants.isKeyDown(client.getWindow(), mainKey.getValue())) {
						client.gui.setScreen(MinecartVisualizerConfigScreen.create(client.gui.screen()));
					}
				}
			}
		});

		//漏斗矿车追踪器
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClientSide() && entity instanceof net.minecraft.world.entity.vehicle.minecart.MinecartHopper minecart && MinecartVisualizerConfig.getInstance().trackingByDye) {
				ItemStack stack = player.getItemInHand(hand);

				if (stack.getItem() instanceof DyeItem dyeItem) {
					DyeColor dyeColor = stack.get(DataComponents.DYE);
					TrackerColor selectedColor = TrackerColor.valueOf(dyeColor.name());
					TrackersManager.setTracker(minecart.getUUID(),minecart.getId(), selectedColor);
					player.sendOverlayMessage(Component.literal("Started tracking with color: " + selectedColor.getLabel()));
					return InteractionResult.SUCCESS;
				}
			}
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != null) {
				TrackersManager.cleanInvalidTracker();
				TrackersManager.tickCounter();
			}
		});
	}
}