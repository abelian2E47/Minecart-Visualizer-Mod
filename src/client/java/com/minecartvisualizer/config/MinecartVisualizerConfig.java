package com.minecartvisualizer.config;

import com.google.gson.GsonBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

public final class MinecartVisualizerConfig {
    public static final ConfigClassHandler<MinecartVisualizerConfig> HANDLER = ConfigClassHandler.createBuilder(MinecartVisualizerConfig.class)
            .id(Identifier.of("minecartvisualizer", "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("minecart_visualizer.json5"))
                    .appendGsonBuilder(GsonBuilder::setPrettyPrinting)
                    .setJson5(true)
                    .build())
            .build();

    // --- 渲染基础设置 ---
    @SerialEntry public boolean enableMinecartVisualization = false;
    @SerialEntry public boolean mergeStackingMinecartInfo = true;
    @SerialEntry public boolean alwaysFacingThePlayer = false;
    @SerialEntry public boolean glowingTrackingMinecart = true;
    @SerialEntry public boolean highlightExtractionTargets = false;
    @SerialEntry public boolean renderHopperRanges = false;
    @SerialEntry public boolean foldInventory = false;
    @SerialEntry public boolean autoSizeColumns = false;
    @SerialEntry public boolean[] hopperSlotFilter = {true, true, true, true, true};
    @SerialEntry public TimeUnit trackerTimeUnit = TimeUnit.TICK;
    @SerialEntry public SpeedUnit speedUnit = SpeedUnit.METERS_PER_SECOND;
    @SerialEntry public int infoRenderDistance = 32;
    public int maxInventorySlotsToRender = 500;

    // --- 信息文本设置 ---
    @SerialEntry public boolean enableDirectionDisplay = true;
    @SerialEntry public boolean enableInfoTextDisplay = false;
    @SerialEntry public boolean enablePosTextDisplay = true;
    @SerialEntry public boolean enableVelocityTextDisplay = false;
    @SerialEntry public boolean enableYawTextDisplay = false;
    @SerialEntry public boolean enableSpeedTextDisplay = true;
    @SerialEntry public boolean enableSignalStrengthDisplay = true;
    @SerialEntry public boolean enableShortIdDisplay = true;
    @SerialEntry public boolean enableStackedCountDisplay = true;
    @SerialEntry public boolean enableTrackerRuntimeDisplay = true;
    @SerialEntry public int accuracy = 3;

    // --- 漏斗矿车专项 ---
    @SerialEntry public boolean enableHopperMinecartEnableDisplay = true;
    @SerialEntry public boolean enableHopperMinecartInventoryDisplay = true;
    @SerialEntry public boolean enableItemStackCountDisplay = true;
    @SerialEntry public int inventoryCols = 5;

    // --- TNT 矿车专项 ---
    @SerialEntry public boolean enableTNTFuseTicksDisplay = true;
    @SerialEntry public boolean enableTNTWobbleDisplay = true;
    @SerialEntry public boolean trackTNTMinecart = true;

    // --- 追踪与调试 ---
    @SerialEntry public boolean trackingByDye = true;
    @SerialEntry public boolean trackMinecartTrail = false;
    @SerialEntry public boolean outputWhenDestroyed = true;
    @SerialEntry public boolean outputWhenSlotChange = true;
    @SerialEntry public int maxTrailPoints = 200;

    public static MinecartVisualizerConfig getInstance() {
        return HANDLER.instance();
    }

    public enum TimeUnit {
        TICK("gt", 1),
        SECOND("s", 20),
        MINUTE("min", 1200);

        private final String label;
        private final int ticksPerUnit;

        TimeUnit(String label, int ticksPerUnit) {
            this.label = label;
            this.ticksPerUnit = ticksPerUnit;
        }

        public String getLabel() { return label; }
        public int getTicksPerUnit() { return ticksPerUnit; }
    }

    public enum SpeedUnit {
        METERS_PER_SECOND("m/s"),
        METERS_PER_TICK("m/gt");

        private final String label;

        SpeedUnit(String label) {
            this.label = label;
        }

        public String getLabel() { return label; }
    }

}
