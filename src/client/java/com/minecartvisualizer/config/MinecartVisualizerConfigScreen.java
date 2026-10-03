package com.minecartvisualizer.config;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.awt.Color;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MinecartVisualizerConfigScreen {

    public static Screen create(Screen parent) {
        MinecartVisualizerConfig config = MinecartVisualizerConfig.getInstance();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("yacl.title.minecart_visualizer"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.translatable("yacl.category.main_settings"))

                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("yacl.group.main_render"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.enable_visualization"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.enable_visualization.desc")))
                                        .binding(false, () -> config.enableMinecartVisualization, v -> config.enableMinecartVisualization = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.always_facing"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.always_facing.desc")))
                                        .binding(true, () -> config.alwaysFacingThePlayer, v -> config.alwaysFacingThePlayer = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.glowing_tracking"))
                                        .binding(true, () -> config.glowingTrackingMinecart, v -> config.glowingTrackingMinecart = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("yacl.option.inventory_cols"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.inventory_cols.desc")))
                                        .binding(5, () -> config.inventoryCols, v -> config.inventoryCols = v)
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                                .range(1, 9)
                                                .step(1))
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.fold_inventory"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.fold_inventory.desc")))
                                        .binding(true, () -> config.foldInventory, v -> config.foldInventory = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.auto_size_columns"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.auto_size_columns.desc")))
                                        .binding(true, () -> config.autoSizeColumns, v -> config.autoSizeColumns = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.merge_stacking_info"))
                                        .binding(true, () -> config.mergeStackingMinecartInfo, v -> config.mergeStackingMinecartInfo = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("yacl.option.max_slots"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.max_slots.desc")))
                                        .binding(0, () -> config.maxInventorySlotsToRender, v -> config.maxInventorySlotsToRender = v)
                                        .controller(opt -> IntegerFieldControllerBuilder.create(opt)
                                                .min(0)
                                                .max(1000)
                                        )
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("yacl.option.render_distance"))
                                        .binding(32, () -> config.infoRenderDistance, v -> config.infoRenderDistance = v)
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(8, 32).step(1))
                                        .build())
                                .option(Option.<MinecartVisualizerConfig.TimeUnit>createBuilder()
                                        .name(Component.translatable("yacl.option.tracker_time_unit"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.tracker_time_unit.desc")))
                                        .binding(
                                                MinecartVisualizerConfig.TimeUnit.SECOND,
                                                () -> config.trackerTimeUnit,
                                                v -> config.trackerTimeUnit = v
                                        )
                                        .controller(opt -> CyclingListControllerBuilder.create(opt)
                                                .values(MinecartVisualizerConfig.TimeUnit.values())
                                                .formatValue(unit -> Component.literal(unit.getLabel()))
                                        )
                                        .build())
                                .option(Option.<MinecartVisualizerConfig.SpeedUnit>createBuilder()
                                        .name(Component.translatable("yacl.option.speed_unit"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.speed_unit.desc")))
                                        .binding(
                                                MinecartVisualizerConfig.SpeedUnit.METERS_PER_SECOND,
                                                () -> config.speedUnit,
                                                v -> config.speedUnit = v
                                        )
                                        .controller(opt -> CyclingListControllerBuilder.create(opt)
                                                .values(MinecartVisualizerConfig.SpeedUnit.values())
                                                .formatValue(unit -> Component.literal(unit.getLabel()))
                                        )
                                        .build())
                                .build())

                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("yacl.group.hopper_filter"))
                                .description(OptionDescription.of(Component.translatable("yacl.group.hopper_filter.desc")))
                                .collapsed(true)
                                .options(List.of(
                                        createSlotOption(0),
                                        createSlotOption(1),
                                        createSlotOption(2),
                                        createSlotOption(3),
                                        createSlotOption(4)
                                ))
                                .build())

                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("yacl.group.text_info"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.enable_text"))
                                        .binding(true, () -> config.enableInfoTextDisplay, v -> config.enableInfoTextDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.info_text_on_top"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.info_text_on_top.desc")))
                                        .binding(true, () -> config.infoTextOnTop, v -> config.infoTextOnTop = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.show_pos"))
                                        .binding(true, () -> config.enablePosTextDisplay, v -> config.enablePosTextDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.show_speed"))
                                        .binding(true, () -> config.enableSpeedTextDisplay, v -> config.enableSpeedTextDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.show_velocity"))
                                        .binding(true, () -> config.enableVelocityTextDisplay, v -> config.enableVelocityTextDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.show_yaw"))
                                        .binding(true, () -> config.enableYawTextDisplay, v -> config.enableYawTextDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.direction"))
                                        .binding(true, () -> config.enableDirectionDisplay, v -> config.enableDirectionDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.show_stacked_count"))
                                        .binding(true, () -> config.enableStackedCountDisplay, v -> config.enableStackedCountDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.show_signal_strength"))
                                        .binding(true, () -> config.enableSignalStrengthDisplay, v -> config.enableSignalStrengthDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.show_tracker_runtime"))
                                        .binding(true, () -> config.enableTrackerRuntimeDisplay, v -> config.enableTrackerRuntimeDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.show_short_id"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.show_short_id.desc")))
                                        .binding(true, () -> config.enableShortIdDisplay, v -> config.enableShortIdDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("yacl.option.accuracy"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.accuracy.desc")))
                                        .binding(3, () -> config.accuracy, v -> config.accuracy = v)
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 10).step(1))
                                        .build())
                                .build())

                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("yacl.group.special_minecarts"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.hopper_range"))
                                        .binding(true, () -> config.renderHopperRanges, v -> config.renderHopperRanges = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.hopper_visual_on_top"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.hopper_visual_on_top.desc")))
                                        .binding(false, () -> config.hopperVisualOnTop, v -> config.hopperVisualOnTop = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.highlight_targets"))
                                        .binding(true, () -> config.highlightExtractionTargets, v -> config.highlightExtractionTargets = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Float>createBuilder()
                                        .name(Component.translatable("yacl.option.hopper_range_scale"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.hopper_range_scale.desc")))
                                        .binding(1.0f, () -> config.hopperRangeBoxScale, v -> config.hopperRangeBoxScale = v)
                                        .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                                .range(0.1f, 3.0f)
                                                .step(0.05f))
                                        .build())
                                .option(Option.<Float>createBuilder()
                                        .name(Component.translatable("yacl.option.extraction_target_scale"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.extraction_target_scale.desc")))
                                        .binding(1.0f, () -> config.extractionTargetBoxScale, v -> config.extractionTargetBoxScale = v)
                                        .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                                .range(0.1f, 4.0f)
                                                .step(0.05f))
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.hopper_locked"))
                                        .binding(true, () -> config.enableHopperMinecartEnableDisplay, v -> config.enableHopperMinecartEnableDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.hopper_inventory"))
                                        .binding(true, () -> config.enableHopperMinecartInventoryDisplay, v -> config.enableHopperMinecartInventoryDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Float>createBuilder()
                                        .name(Component.translatable("yacl.option.inventory_slot_size"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.inventory_slot_size.desc")))
                                        .binding(1.0f, () -> config.inventorySlotSize, v -> config.inventorySlotSize = v)
                                        .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                                .range(0.5f, 2.0f)
                                                .step(0.05f))
                                        .build())
                                .option(Option.<Float>createBuilder()
                                        .name(Component.translatable("yacl.option.inventory_item_size"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.inventory_item_size.desc")))
                                        .binding(1.0f, () -> config.inventoryItemSize, v -> config.inventoryItemSize = v)
                                        .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                                .range(0.5f, 2.0f)
                                                .step(0.05f))
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.item_stack_count"))
                                        .binding(true, () -> config.enableItemStackCountDisplay, v -> config.enableItemStackCountDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.tnt_fuse"))
                                        .binding(true, () -> config.enableTNTFuseTicksDisplay, v -> config.enableTNTFuseTicksDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.tnt_wobble"))
                                        .binding(true, () -> config.enableTNTWobbleDisplay, v -> config.enableTNTWobbleDisplay = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .build())

                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("yacl.group.colors"))
                                .description(OptionDescription.of(Component.translatable("yacl.group.colors.desc")))
                                .collapsed(true)
                                .option(createColorOption(
                                        "yacl.option.color_pickup_range",
                                        MinecartVisualizerConfig.DEFAULT_PICKUP_RANGE_COLOR,
                                        c -> c.pickupRangeColor, (c, v) -> c.pickupRangeColor = v))
                                .option(createColorOption(
                                        "yacl.option.color_extraction_range",
                                        MinecartVisualizerConfig.DEFAULT_EXTRACTION_RANGE_COLOR,
                                        c -> c.extractionRangeColor, (c, v) -> c.extractionRangeColor = v))
                                .option(createColorOption(
                                        "yacl.option.color_extraction_target",
                                        MinecartVisualizerConfig.DEFAULT_EXTRACTION_TARGET_COLOR,
                                        c -> c.extractionTargetColor, (c, v) -> c.extractionTargetColor = v))
                                .option(createColorOption(
                                        "yacl.option.color_info_text",
                                        MinecartVisualizerConfig.DEFAULT_INFO_TEXT_COLOR,
                                        c -> c.infoTextColor, (c, v) -> c.infoTextColor = v))
                                .option(createColorOption(
                                        "yacl.option.color_item_count_text",
                                        MinecartVisualizerConfig.DEFAULT_ITEM_COUNT_TEXT_COLOR,
                                        c -> c.itemCountTextColor, (c, v) -> c.itemCountTextColor = v))
                                .option(createColorOption(
                                        "yacl.option.color_slot_background",
                                        MinecartVisualizerConfig.DEFAULT_SLOT_BACKGROUND_COLOR,
                                        c -> c.slotBackgroundColor, (c, v) -> c.slotBackgroundColor = v))
                                .option(createColorOption(
                                        "yacl.option.color_slot_border",
                                        MinecartVisualizerConfig.DEFAULT_SLOT_BORDER_COLOR,
                                        c -> c.slotBorderColor, (c, v) -> c.slotBorderColor = v))
                                .option(createColorOption(
                                        "yacl.option.color_slot_background_locked",
                                        MinecartVisualizerConfig.DEFAULT_SLOT_BACKGROUND_LOCKED_COLOR,
                                        c -> c.slotBackgroundLockedColor, (c, v) -> c.slotBackgroundLockedColor = v))
                                .option(createColorOption(
                                        "yacl.option.color_slot_border_locked",
                                        MinecartVisualizerConfig.DEFAULT_SLOT_BORDER_LOCKED_COLOR,
                                        c -> c.slotBorderLockedColor, (c, v) -> c.slotBorderLockedColor = v))
                                .option(createColorOption(
                                        "yacl.option.color_tracker_point_active",
                                        MinecartVisualizerConfig.DEFAULT_TRACKER_POINT_ACTIVE_COLOR,
                                        c -> c.trackerPointActiveColor, (c, v) -> c.trackerPointActiveColor = v))
                                .option(createColorOption(
                                        "yacl.option.color_tracker_point_inactive",
                                        MinecartVisualizerConfig.DEFAULT_TRACKER_POINT_INACTIVE_COLOR,
                                        c -> c.trackerPointInactiveColor, (c, v) -> c.trackerPointInactiveColor = v))
                                .option(createColorOption(
                                        "yacl.option.color_tracker_trail",
                                        MinecartVisualizerConfig.DEFAULT_TRACKER_TRAIL_COLOR,
                                        c -> c.trackerTrailColor, (c, v) -> c.trackerTrailColor = v))
                                .build())

                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("yacl.group.track"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.track_trail"))
                                        .binding(true, () -> config.trackMinecartTrail, v -> config.trackMinecartTrail = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.tracker_use_dye_color"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.tracker_use_dye_color.desc")))
                                        .binding(true, () -> config.trackerPointUseDyeColor, v -> config.trackerPointUseDyeColor = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("yacl.option.max_trail_points"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.max_trail_points.desc")))
                                        .binding(200, () -> config.maxTrailPoints, v -> config.maxTrailPoints = v)
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 1000).step(50))
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.output_slot_change"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.output_slot_change.desc")))
                                        .binding(true, () -> config.outputWhenSlotChange, v -> config.outputWhenSlotChange = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.output_destroyed"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.output_destroyed.desc")))
                                        .binding(true, () -> config.outputWhenDestroyed, v -> config.outputWhenDestroyed = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.output_on_increase"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.output_on_increase.desc")))
                                        .binding(true, () -> config.outputOnIncrease, v -> config.outputOnIncrease = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.output_on_decrease"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.output_on_decrease.desc")))
                                        .binding(true, () -> config.outputOnDecrease, v -> config.outputOnDecrease = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.print_inventory"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.print_inventory.desc")))
                                        .binding(true, () -> config.printInventory, v -> config.printInventory = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.print_duration"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.print_duration.desc")))
                                        .binding(true, () -> config.printDuration, v -> config.printDuration = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())

                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.print_coordinates"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.print_coordinates.desc")))
                                        .binding(true, () -> config.printPosition, v -> config.printPosition = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.tracking_by_dye"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.tracking_by_dye.desc")))
                                        .binding(true, () -> config.trackingByDye, v -> config.trackingByDye = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.track_tnt"))
                                        .binding(true, () -> config.trackTNTMinecart, v -> config.trackTNTMinecart = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .build())

                        .group(OptionGroup.createBuilder()
                                .name(Component.translatable("yacl.group.collision"))
                                .description(OptionDescription.of(Component.translatable("yacl.group.collision.desc")))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.output_on_collision"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.output_on_collision.desc")))
                                        .binding(false, () -> config.outputOnCollision, v -> config.outputOnCollision = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Float>createBuilder()
                                        .name(Component.translatable("yacl.option.collision_threshold"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.collision_threshold.desc")))
                                        .binding(0.02f, () -> config.collisionMomentumThreshold, v -> config.collisionMomentumThreshold = v)
                                        .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                                .range(0.0f, 2.0f)
                                                .step(0.01f))
                                        .build())
                                .option(Option.<Integer>createBuilder()
                                        .name(Component.translatable("yacl.option.collision_cooldown"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.collision_cooldown.desc")))
                                        .binding(10, () -> config.collisionMessageCooldown, v -> config.collisionMessageCooldown = v)
                                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                                .range(0, 100)
                                                .step(1))
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.print_collision_position"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.print_collision_position.desc")))
                                        .binding(true, () -> config.printCollisionPosition, v -> config.printCollisionPosition = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.print_collision_momentum"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.print_collision_momentum.desc")))
                                        .binding(false, () -> config.printCollisionMomentum, v -> config.printCollisionMomentum = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.print_collision_speed_change"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.print_collision_speed_change.desc")))
                                        .binding(false, () -> config.printCollisionSpeedChange, v -> config.printCollisionSpeedChange = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .option(Option.<Boolean>createBuilder()
                                        .name(Component.translatable("yacl.option.print_collision_target"))
                                        .description(OptionDescription.of(Component.translatable("yacl.option.print_collision_target.desc")))
                                        .binding(true, () -> config.printCollisionTarget, v -> config.printCollisionTarget = v)
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .build())
                        .build())
                .save(MinecartVisualizerConfig.HANDLER::save)
                .build()
                .generateScreen(parent);
    }

    private static Option<Boolean> createSlotOption(int index) {
        MinecartVisualizerConfig config = MinecartVisualizerConfig.getInstance();
        return Option.<Boolean>createBuilder()
                .name(Component.translatable("yacl.minecartvisualizer.text.slot" ).append(" " + (index + 1)))
                .binding(true, () -> config.hopperSlotFilter[index], v -> config.hopperSlotFilter[index] = v)
                .controller(TickBoxControllerBuilder::create)
                .build();
    }

    private static Option<Color> createColorOption(String nameKey, Color fallback,
                                                   Function<MinecartVisualizerConfig, Color> getter,
                                                   BiConsumer<MinecartVisualizerConfig, Color> setter) {
        MinecartVisualizerConfig config = MinecartVisualizerConfig.getInstance();
        return Option.<Color>createBuilder()
                .name(Component.translatable(nameKey))
                .description(OptionDescription.of(Component.translatable(nameKey + ".desc")))
                .binding(fallback, () -> {
                    Color current = getter.apply(config);
                    return current != null ? current : fallback;
                }, v -> setter.accept(config, v))
                .controller(opt -> ColorControllerBuilder.create(opt).allowAlpha(false))
                .build();
    }

}
