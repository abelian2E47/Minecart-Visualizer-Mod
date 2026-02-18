package com.minecartvisualizer;

import com.minecartvisualizer.command.MinecartVisualizerCommands;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.config.MinecartVisualizerConfigScreen;
import com.minecartvisualizer.tracker.TrackerColor;
import com.minecartvisualizer.tracker.TrackersManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

import java.util.*;


public class MinecartVisualizerClient implements ClientModInitializer {

	public static KeyBinding mainConfigKey;
	public static KeyBinding subConfigKey;
	public static UUID uuid;

	public void onInitializeClient() {
		MinecartVisualizerConfig.HANDLER.load();

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			MinecartVisualizerConfig.HANDLER.save();
		});

		MinecartClientHandler.register();
		MinecartVisualizerCommands.registerCommands();

		mainConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.minecartvisualizer.config_main",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_C,
				"category.minecartvisualizer.title"
		));

		subConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.minecartvisualizer.config_sub",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_V,
				"category.minecartvisualizer.title"
		));

		//配置界面快捷键
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;

			InputUtil.Key mainKey = InputUtil.fromTranslationKey(mainConfigKey.getBoundKeyTranslationKey());
			InputUtil.Key subKey = InputUtil.fromTranslationKey(subConfigKey.getBoundKeyTranslationKey());

			if (mainKey.equals(InputUtil.UNKNOWN_KEY)) return;

			boolean isSubKeyNone = subKey.equals(InputUtil.UNKNOWN_KEY);

			if (isSubKeyNone) {
				while (mainConfigKey.wasPressed()) {
					client.setScreen(MinecartVisualizerConfigScreen.create(client.currentScreen));
				}
			} else {
				while (subConfigKey.wasPressed()) {
					long windowHandle = client.getWindow().getHandle();
					if (InputUtil.isKeyPressed(windowHandle, mainKey.getCode())) {
						client.setScreen(MinecartVisualizerConfigScreen.create(client.currentScreen));
					}
				}
			}
		});

		//漏斗矿车追踪器
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.isClient && entity instanceof net.minecraft.entity.vehicle.HopperMinecartEntity minecart && MinecartVisualizerConfig.getInstance().trackingByDye) {
				ItemStack stack = player.getStackInHand(hand);

				if (stack.getItem() instanceof DyeItem dyeItem) {
					TrackerColor selectedColor = TrackerColor.valueOf(dyeItem.getColor().name());
					TrackersManager.setTracker(minecart.getUuid(),minecart.getId(), selectedColor);
					player.sendMessage(Text.literal("Started tracking with color: " + selectedColor.getLabel()), true);
					return ActionResult.SUCCESS;
				}
			}
			return ActionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.world != null) {
				TrackersManager.cleanInvalidTracker();
				TrackersManager.tickCounter();
			}
		});
	}
}