package com.minecartvisualizer.config;

import com.google.gson.GsonBuilder;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.awt.Color;

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
    @SerialEntry public boolean enableMinecartVisualization = true;
    @SerialEntry public boolean mergeStackingMinecartInfo = true;
    @SerialEntry public boolean alwaysFacingThePlayer = false;
    @SerialEntry public boolean glowingTrackingMinecart = true;
    @SerialEntry public boolean highlightExtractionTargets = false;
    @SerialEntry public boolean renderHopperRanges = false;
    @SerialEntry public boolean hopperVisualOnTop = false;
    @SerialEntry public float hopperRangeBoxScale = 1.0f;
    @SerialEntry public float extractionTargetBoxScale = 1.0f;
    @SerialEntry public boolean foldInventory = false;
    @SerialEntry public boolean autoSizeColumns = false;
    @SerialEntry public boolean[] hopperSlotFilter = {true, true, true, true, true};
    @SerialEntry public TimeUnit trackerTimeUnit = TimeUnit.TICK;
    @SerialEntry public SpeedUnit speedUnit = SpeedUnit.METERS_PER_SECOND;
    @SerialEntry public int infoRenderDistance = 32;
    public int maxInventorySlotsToRender = 500;

    // --- 信息文本设置 ---
    @SerialEntry public boolean enableDirectionDisplay = true;
    @SerialEntry public boolean enableInfoTextDisplay = true;
    @SerialEntry public boolean infoTextOnTop = true;
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
    @SerialEntry public float inventorySlotSize = 1.0f;
    @SerialEntry public float inventoryItemSize = 1.0f;

    // --- TNT 矿车专项 ---
    @SerialEntry public boolean enableTNTFuseTicksDisplay = true;
    @SerialEntry public boolean enableTNTWobbleDisplay = true;
    @SerialEntry public boolean trackTNTMinecart = true;

    // --- 颜色 ---
    public static final Color DEFAULT_PICKUP_RANGE_COLOR = new Color(0xFFFF1A);
    public static final Color DEFAULT_EXTRACTION_RANGE_COLOR = new Color(0xFF991A);
    public static final Color DEFAULT_EXTRACTION_TARGET_COLOR = new Color(0x2BFF4D);
    public static final Color DEFAULT_TRACKER_POINT_ACTIVE_COLOR = new Color(0x2BFF4D);
    public static final Color DEFAULT_TRACKER_POINT_INACTIVE_COLOR = new Color(0x8C8C8C);
    public static final Color DEFAULT_TRACKER_TRAIL_COLOR = new Color(0x2BFF4D);
    public static final Color DEFAULT_SLOT_BACKGROUND_COLOR = new Color(0x878787);
    public static final Color DEFAULT_SLOT_BORDER_COLOR = new Color(0xE6E6E6);
    public static final Color DEFAULT_SLOT_BACKGROUND_LOCKED_COLOR = new Color(0x663B3B);
    public static final Color DEFAULT_SLOT_BORDER_LOCKED_COLOR = new Color(0x990000);
    public static final Color DEFAULT_ITEM_COUNT_TEXT_COLOR = new Color(0xFFFFFF);
    public static final Color DEFAULT_INFO_TEXT_COLOR = new Color(0xFFFFFF);

    @SerialEntry public Color pickupRangeColor = new Color(0xFFFF1A);
    @SerialEntry public Color extractionRangeColor = new Color(0xFF991A);
    @SerialEntry public Color extractionTargetColor = new Color(0x2BFF4D);
    @SerialEntry public Color trackerPointActiveColor = new Color(0x2BFF4D);
    @SerialEntry public Color trackerPointInactiveColor = new Color(0x8C8C8C);
    @SerialEntry public Color trackerTrailColor = new Color(0x2BFF4D);
    @SerialEntry public Color slotBackgroundColor = new Color(0x878787);
    @SerialEntry public Color slotBorderColor = new Color(0xE6E6E6);
    @SerialEntry public Color slotBackgroundLockedColor = new Color(0x663B3B);
    @SerialEntry public Color slotBorderLockedColor = new Color(0x990000);
    @SerialEntry public Color itemCountTextColor = new Color(0xFFFFFF);
    @SerialEntry public Color infoTextColor = new Color(0xFFFFFF);

    // --- 追踪与调试 ---
    @SerialEntry public boolean trackingByDye = true;
    @SerialEntry public boolean trackerPointUseDyeColor = true;
    @SerialEntry public boolean trackMinecartTrail = false;
    @SerialEntry public boolean outputWhenDestroyed = true;
    @SerialEntry public boolean outputWhenSlotChange = true;
    @SerialEntry public boolean outputOnIncrease = true;
    @SerialEntry public boolean outputOnDecrease = true;
    @SerialEntry public boolean printInventory = true;
    @SerialEntry public boolean printDuration = true;
    @SerialEntry public boolean printPosition = true;
    @SerialEntry public int maxTrailPoints = 200;

    // --- 矿车挤压提示 ---
    @SerialEntry public boolean outputOnCollision = false;
    @SerialEntry public float collisionMomentumThreshold = 0.02f;
    @SerialEntry public int collisionMessageCooldown = 10;
    @SerialEntry public boolean printCollisionPosition = true;
    @SerialEntry public boolean printCollisionMomentum = false;
    @SerialEntry public boolean printCollisionSpeedChange = false;
    @SerialEntry public boolean printCollisionTarget = true;

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
