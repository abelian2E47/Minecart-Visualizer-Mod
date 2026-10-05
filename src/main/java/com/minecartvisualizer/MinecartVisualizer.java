package com.minecartvisualizer;

import net.fabricmc.api.ModInitializer;

import net.minecraft.util.Identifier;


public class MinecartVisualizer implements ModInitializer {
	public static final String MOD_ID = "minecartvisualizer";
	public static final Identifier MINECART_DATA_PACKET_ID = Identifier.of(MOD_ID,"minecart_data_packet");
	public static final Identifier HOPPER_MINECART_DATA_PACKET_ID = Identifier.of(MOD_ID,"hopper_minecart_data_packet");
	public static final Identifier TNT_MINECART_DATA_PACKET_ID = Identifier.of(MOD_ID,"tnt_minecart_data_packet");
	public static final Identifier MINECART_COLLISION_PACKET_ID = Identifier.of(MOD_ID,"minecart_collision_packet");

	@Override
	public void onInitialize() {
		//1.20.x 的自定义通道直接用 Identifier 标识，不需要像 1.21.1 那样先注册 payload 类型
	}
}