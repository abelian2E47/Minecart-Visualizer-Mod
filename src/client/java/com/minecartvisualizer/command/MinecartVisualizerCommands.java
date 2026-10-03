package com.minecartvisualizer.command;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.*;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class MinecartVisualizerCommands {
    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var config = MinecartVisualizerConfig.getInstance();

            dispatcher.register(ClientCommandManager.literal("MinecartVisualizer")
                    //设置 (Setting)
                    .then(ClientCommandManager.literal("setting")
                            .then(registerBool("InfoTextDisplay", v -> config.enableInfoTextDisplay = v))
                            .then(registerBool("AlwaysFacingThePlayer", v -> config.alwaysFacingThePlayer = v))
                            .then(registerBool("MergeStackingMinecartInfo", v -> config.mergeStackingMinecartInfo = v))
                    )
                    //过滤器 (Filter)
                    .then(ClientCommandManager.literal("filter")
                            .then(ClientCommandManager.argument("color", StringArgumentType.string())
                                    .suggests((c, b) -> CommandSource.suggestMatching(Arrays.stream(TrackerColor.values()).map(Enum::name).map(String::toLowerCase), b))
                                    .then(ClientCommandManager.argument("listType", StringArgumentType.string())
                                            .suggests((c, b) -> CommandSource.suggestMatching(new String[]{"white", "black"}, b))
                                            .executes(MinecartVisualizerCommands::executeFilterToggle)
                                            .then(ClientCommandManager.literal("add")
                                                    .then(ClientCommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                                            .executes(ctx -> executeFilterAction(ctx, true, false)))
                                                    .then(ClientCommandManager.literal("hand")
                                                            .executes(ctx -> executeFilterAction(ctx, true, true))))
                                            .then(ClientCommandManager.literal("remove")
                                                    .then(ClientCommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                                            .executes(ctx -> executeFilterAction(ctx, false, false)))
                                                    .then(ClientCommandManager.literal("hand")
                                                            .executes(ctx -> executeFilterAction(ctx, false, true))))
                                            .then(ClientCommandManager.literal("clear")
                                                    .executes(MinecartVisualizerCommands::executeFilterClear))
                                            .then(ClientCommandManager.literal("list")
                                                    .executes(MinecartVisualizerCommands::executeFilterList))
                                    )
                            )
                    )
                    //计数器 (Counter)
                    .then(ClientCommandManager.literal("counter")
                            .then(ClientCommandManager.argument("color", StringArgumentType.string())
                                    .suggests((c, b) -> CommandSource.suggestMatching(Arrays.stream(TrackerColor.values()).map(Enum::name).map(String::toLowerCase), b))
                                    .executes(MinecartVisualizerCommands::executeCounterToggle)
                                    .then(ClientCommandManager.literal("reset").executes(MinecartVisualizerCommands::executeCounterReset))
                                    .then(ClientCommandManager.literal("print").executes(MinecartVisualizerCommands::executeCounterPrint))
                            )
                    )

                    .then(ClientCommandManager.literal("point")
                            .then(ClientCommandManager.literal("add")
                                    .then(ClientCommandManager.argument("color", StringArgumentType.string())
                                            .suggests((c, b) -> CommandSource.suggestMatching(Arrays.stream(TrackerColor.values()).map(Enum::name).map(String::toLowerCase), b))
                                            .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                                    .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                                            .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                                                    .executes(MinecartVisualizerCommands::executePointAdd))))
                                            .then(ClientCommandManager.literal("look")
                                                    .executes(MinecartVisualizerCommands::executePointAddLook))))
                            .then(ClientCommandManager.literal("remove")
                                    .then(ClientCommandManager.argument("pos", StringArgumentType.greedyString())
                                            .suggests((c, b) -> CommandSource.suggestMatching(
                                                    TrackerPointsManager.getPoints().keySet().stream()
                                                            .map(p -> p.getX() + " " + p.getY() + " " + p.getZ()), b))
                                            .executes(MinecartVisualizerCommands::executePointRemovePosString))
                                    .then(ClientCommandManager.argument("color", StringArgumentType.string())
                                            .suggests((c, b) -> CommandSource.suggestMatching(Arrays.stream(TrackerColor.values()).map(Enum::name), b))
                                            .executes(MinecartVisualizerCommands::executePointRemoveColor)))
                            .then(ClientCommandManager.literal("list").executes(MinecartVisualizerCommands::executePointList))
                            .then(ClientCommandManager.literal("clear").executes(MinecartVisualizerCommands::executePointClear))
                    )
                    //主命令切换
                    .executes(ctx -> {
                        config.enableMinecartVisualization = !config.enableMinecartVisualization;
                        MinecartVisualizerConfig.HANDLER.save();
                        ctx.getSource().sendFeedback(Text.literal("§a[MinecartVisualizer] §fVisualization is now: " + (config.enableMinecartVisualization ? "§eON" : "§7OFF")));
                        return 1;
                    })
            );
        });
    }

    // --- 工具方法 ---

    private static LiteralArgumentBuilder<FabricClientCommandSource> registerBool(String name, Consumer<Boolean> setter) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean val = BoolArgumentType.getBool(ctx, "value");
                            setter.accept(val);
                            MinecartVisualizerConfig.HANDLER.save();
                            ctx.getSource().sendFeedback(Text.literal("§a[MinecartVisualizer] §f" + name + " set to: §e" + val));
                            return 1;
                        }));
    }

    // --- 执行方法 ---
    private static int executeFilterAction(CommandContext<FabricClientCommandSource> context, boolean isAdd, boolean isHand) {
        String colorName = StringArgumentType.getString(context, "color");
        String listType = StringArgumentType.getString(context, "listType");
        TrackerColor color = TrackerColor.valueOf(colorName.toUpperCase());
        TrackerFilter filter = TrackersManager.filters.get(color);

        String itemId = "";
        if (isHand) {
            ItemStack handStack = null;
            if (MinecraftClient.getInstance().player != null) {
                handStack = MinecraftClient.getInstance().player.getMainHandStack();
            }
            if (handStack != null && handStack.isEmpty()) {
                context.getSource().sendError(Text.literal("Hand is empty!"));
                return 0;
            }
            if (handStack != null) {
                itemId = Registries.ITEM.getId(handStack.getItem()).toString();
            }
        } else {
            itemId = Registries.ITEM.getId(ItemStackArgumentType.getItemStackArgument(context, "item").getItem()).toString();
        }

        boolean isWhite = listType.equalsIgnoreCase("white");
        if (isAdd) {
            if (isWhite) filter.addWhiteList(itemId); else filter.addBlackList(itemId);
        } else {
            if (isWhite) filter.removeWhiteList(itemId); else filter.removeBlackList(itemId);
        }

        context.getSource().sendFeedback(Text.literal("§a[Filter] " + (isAdd ? "Added " : "Removed ") + "§e" + itemId + "§f to " + colorName + " " + listType));
        return 1;
    }

    private static int executeFilterToggle(CommandContext<FabricClientCommandSource> context) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(context, "color").toUpperCase());
        String listType = StringArgumentType.getString(context, "listType");
        TrackerFilter filter = TrackersManager.filters.get(color);

        if (listType.equalsIgnoreCase("white")) {
            filter.toggleWhiteList();
            context.getSource().sendFeedback(Text.literal("§a[Filter] §fWhiteList for " + color.name() + ": " + (filter.enableWhiteList ? "§eON" : "§7OFF")));
        } else {
            filter.toggleBlackList();
            context.getSource().sendFeedback(Text.literal("§a[Filter] §fBlackList for " + color.name() + ": " + (filter.enableBlackList ? "§eON" : "§7OFF")));
        }
        return 1;
    }

    private static int executeFilterClear(CommandContext<FabricClientCommandSource> context) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(context, "color").toUpperCase());
        String listType = StringArgumentType.getString(context, "listType");
        TrackerFilter filter = TrackersManager.filters.get(color);
        if (listType.equalsIgnoreCase("white")) filter.clearWhiteList(); else filter.clearBlackList();
        context.getSource().sendFeedback(Text.literal("§c[Filter] Cleared " + color.name() + " " + listType + " list"));
        return 1;
    }

    private static int executeFilterList(CommandContext<FabricClientCommandSource> context) {
        String colorName = StringArgumentType.getString(context, "color");
        String listType = StringArgumentType.getString(context, "listType");
        TrackerColor color = TrackerColor.valueOf(colorName.toUpperCase());
        TrackerFilter filter = TrackersManager.filters.get(color);
        List<String> list = listType.equalsIgnoreCase("white") ? filter.whiteList : filter.blackList;

        context.getSource().sendFeedback(Text.literal("§6--- " + colorName + " " + listType.toUpperCase() + " LIST ---"));
        if (list.isEmpty()) {
            context.getSource().sendFeedback(Text.literal(" §8(Empty)"));
        } else {
            for (String id : list) {
                MutableText feedbackText = Text.literal(" §7- §f" + id).styled(s -> s
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Click to remove")))
                        .withClickEvent(new ClickEvent.SuggestCommand(
                                "/MinecartVisualizer filter " + colorName + " " + listType + " remove " + id
                        ))
                );
                context.getSource().sendFeedback(feedbackText);
            }
        }
        return 1;
    }

    private static int executeCounterToggle(CommandContext<FabricClientCommandSource> ctx) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(ctx, "color").toUpperCase());
        TrackerCounter counter = TrackersManager.getCounter(color);
        counter.toggle();
        ctx.getSource().sendFeedback(Text.literal("§a[Counter] §f" + color.name() + " is now " + (counter.isEnable() ? "§eENABLED" : "§7DISABLED")));
        return 1;
    }

    private static int executeCounterReset(CommandContext<FabricClientCommandSource> ctx) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(ctx, "color").toUpperCase());
        TrackersManager.getCounter(color).reset();
        ctx.getSource().sendFeedback(Text.literal("§e[Counter] §fReset " + color.name()));
        return 1;
    }

    private static int executeCounterPrint(CommandContext<FabricClientCommandSource> ctx) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(ctx, "color").toUpperCase());
        if (MinecraftClient.getInstance().player != null) {
            TrackersManager.getCounter(color).printCounterReport(MinecraftClient.getInstance().player);
        }
        return 1;
    }

    private static int executePointAdd(CommandContext<FabricClientCommandSource> context) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(context, "color").toUpperCase());
        int x = IntegerArgumentType.getInteger(context, "x");
        int y = IntegerArgumentType.getInteger(context, "y");
        int z = IntegerArgumentType.getInteger(context, "z");
        BlockPos pos = new BlockPos(x, y, z);

        TrackerPointsManager.getInstance().addPoint(color, pos);
        context.getSource().sendFeedback(Text.literal("§a[Point] §fAdded ")
                .append(Text.literal(color.name()).styled(s -> s.withColor(color.getHex())))
                .append(" at " + pos.toShortString()));
        return 1;
    }

    private static int executePointAddLook(CommandContext<FabricClientCommandSource> context) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(context, "color").toUpperCase());

        HitResult hit = MinecraftClient.getInstance().crosshairTarget;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            TrackerPointsManager.getInstance().addPoint(color, pos);
            context.getSource().sendFeedback(Text.literal("§a[Point] §fAdded ")
                    .append(Text.literal(color.name()).styled(s -> s.withColor(color.getHex())))
                    .append(" at §e" + pos.toShortString() + " §8(Look)"));
        } else {
            context.getSource().sendError(Text.literal("§cYou are not looking at a block!"));
        }
        return 1;
    }

    private static int executePointRemovePosString(CommandContext<FabricClientCommandSource> context) {
        String posStr = StringArgumentType.getString(context, "pos");
        try {
            String[] parts = posStr.split(" ");
            if (parts.length == 3) {
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                BlockPos pos = new BlockPos(x, y, z);

                TrackerPointsManager.getInstance().removePoint(pos);
                context.getSource().sendFeedback(Text.literal("§e[Point] §fRemoved point at " + pos.toShortString()));
            }
        } catch (Exception e) {
            context.getSource().sendError(Text.literal("§cInvalid coordinates format! Use 'x y z'"));
        }
        return 1;
    }

    private static int executePointRemoveColor(CommandContext<FabricClientCommandSource> ctx) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(ctx, "color").toUpperCase());
        TrackerPointsManager.getInstance().removePoint(color);
        ctx.getSource().sendFeedback(Text.literal("§e[Point] §fRemoved all points with color ")
                .append(Text.literal(color.name()).styled(s -> s.withColor(color.getHex()))));
        return 1;
    }

    private static int executePointClear(CommandContext<FabricClientCommandSource> ctx) {
        TrackerPointsManager.getInstance().clearAllPoints();
        ctx.getSource().sendFeedback(Text.literal("§c[Point] Cleared all tracking points"));
        return 1;
    }

    private static int executePointList(CommandContext<FabricClientCommandSource> ctx) {
        var points = TrackerPointsManager.getPoints();
        ctx.getSource().sendFeedback(Text.literal("§6--- TRACKING POINTS ---"));

        if (points.isEmpty()) {
            ctx.getSource().sendFeedback(Text.literal(" §8(Empty)"));
        } else {
            points.forEach((pos, state) -> {
                Text posText = Text.literal("[" + pos.toShortString() + "]")
                        .styled(style -> style
                                .withColor(state.getColor().getHex())
                                // 修正：使用 ClickEvent.SuggestCommand 记录类
                                .withClickEvent(new ClickEvent.SuggestCommand(
                                        "/tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                                ))
                                // 修正：使用 HoverEvent.ShowText 记录类
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Text.literal("Click to prepare TP command")
                                ))
                        );

                Text finalFeedback = Text.literal(" §7- ")
                        .append(Text.literal(state.getColor().name() + ": ")
                                .styled(s -> s.withColor(state.getColor().getHex())))
                        .append(posText);
                ctx.getSource().sendFeedback(finalFeedback);
            });
        }
        return 1;
    }

}
