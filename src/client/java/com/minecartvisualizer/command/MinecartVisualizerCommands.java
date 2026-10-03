package com.minecartvisualizer.command;
import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.tracker.*;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class MinecartVisualizerCommands {
    public static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var config = MinecartVisualizerConfig.getInstance();

            dispatcher.register(ClientCommands.literal("MinecartVisualizer")
                    //设置 (Setting)
                    .then(ClientCommands.literal("setting")
                            .then(registerBool("InfoTextDisplay", v -> config.enableInfoTextDisplay = v))
                            .then(registerBool("AlwaysFacingThePlayer", v -> config.alwaysFacingThePlayer = v))
                            .then(registerBool("MergeStackingMinecartInfo", v -> config.mergeStackingMinecartInfo = v))
                    )
                    //过滤器 (Filter)
                    .then(ClientCommands.literal("filter")
                            .then(ClientCommands.argument("color", StringArgumentType.string())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(TrackerColor.values()).map(Enum::name).map(String::toLowerCase), b))
                                    .then(ClientCommands.argument("listType", StringArgumentType.string())
                                            .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"white", "black"}, b))
                                            .executes(MinecartVisualizerCommands::executeFilterToggle)
                                            .then(ClientCommands.literal("add")
                                                    .then(ClientCommands.argument("item", ItemArgument.item(registryAccess))
                                                            .executes(ctx -> executeFilterAction(ctx, true, false)))
                                                    .then(ClientCommands.literal("hand")
                                                            .executes(ctx -> executeFilterAction(ctx, true, true))))
                                            .then(ClientCommands.literal("remove")
                                                    .then(ClientCommands.argument("item", ItemArgument.item(registryAccess))
                                                            .executes(ctx -> executeFilterAction(ctx, false, false)))
                                                    .then(ClientCommands.literal("hand")
                                                            .executes(ctx -> executeFilterAction(ctx, false, true))))
                                            .then(ClientCommands.literal("clear")
                                                    .executes(MinecartVisualizerCommands::executeFilterClear))
                                            .then(ClientCommands.literal("list")
                                                    .executes(MinecartVisualizerCommands::executeFilterList))
                                    )
                            )
                    )
                    //计数器 (Counter)
                    .then(ClientCommands.literal("counter")
                            .then(ClientCommands.argument("color", StringArgumentType.string())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(TrackerColor.values()).map(Enum::name).map(String::toLowerCase), b))
                                    .executes(MinecartVisualizerCommands::executeCounterToggle)
                                    .then(ClientCommands.literal("reset").executes(MinecartVisualizerCommands::executeCounterReset))
                                    .then(ClientCommands.literal("print").executes(MinecartVisualizerCommands::executeCounterPrint))
                            )
                    )

                    .then(ClientCommands.literal("point")
                            .then(ClientCommands.literal("add")
                                    .then(ClientCommands.argument("color", StringArgumentType.string())
                                            .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(TrackerColor.values()).map(Enum::name).map(String::toLowerCase), b))
                                            .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                                                    .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                                                            .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                                                                    .executes(MinecartVisualizerCommands::executePointAdd))))
                                            .then(ClientCommands.literal("look")
                                                    .executes(MinecartVisualizerCommands::executePointAddLook))))
                            .then(ClientCommands.literal("remove")
                                    .then(ClientCommands.argument("pos", StringArgumentType.greedyString())
                                            .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                    TrackerPointsManager.getPoints().keySet().stream()
                                                            .map(p -> p.getX() + " " + p.getY() + " " + p.getZ()), b))
                                            .executes(MinecartVisualizerCommands::executePointRemovePosString))
                                    .then(ClientCommands.argument("color", StringArgumentType.string())
                                            .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(TrackerColor.values()).map(Enum::name), b))
                                            .executes(MinecartVisualizerCommands::executePointRemoveColor)))
                            .then(ClientCommands.literal("list").executes(MinecartVisualizerCommands::executePointList))
                            .then(ClientCommands.literal("clear").executes(MinecartVisualizerCommands::executePointClear))
                    )
                    //主命令切换
                    .executes(ctx -> {
                        config.enableMinecartVisualization = !config.enableMinecartVisualization;
                        MinecartVisualizerConfig.HANDLER.save();
                        ctx.getSource().sendFeedback(Component.literal("§a[MinecartVisualizer] §fVisualization is now: " + (config.enableMinecartVisualization ? "§eON" : "§7OFF")));
                        return 1;
                    })
            );
        });
    }

    // --- 工具方法 ---

    private static LiteralArgumentBuilder<FabricClientCommandSource> registerBool(String name, Consumer<Boolean> setter) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> {
                            boolean val = BoolArgumentType.getBool(ctx, "value");
                            setter.accept(val);
                            MinecartVisualizerConfig.HANDLER.save();
                            ctx.getSource().sendFeedback(Component.literal("§a[MinecartVisualizer] §f" + name + " set to: §e" + val));
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
            if (Minecraft.getInstance().player != null) {
                handStack = Minecraft.getInstance().player.getMainHandItem();
            }
            if (handStack != null && handStack.isEmpty()) {
                context.getSource().sendError(Component.literal("Hand is empty!"));
                return 0;
            }
            if (handStack != null) {
                itemId = BuiltInRegistries.ITEM.getKey(handStack.getItem()).toString();
            }
        } else {
            itemId = BuiltInRegistries.ITEM.getKey(ItemArgument.getItem(context, "item").item().value()).toString();
        }

        boolean isWhite = listType.equalsIgnoreCase("white");
        if (isAdd) {
            if (isWhite) filter.addWhiteList(itemId); else filter.addBlackList(itemId);
        } else {
            if (isWhite) filter.removeWhiteList(itemId); else filter.removeBlackList(itemId);
        }

        context.getSource().sendFeedback(Component.literal("§a[Filter] " + (isAdd ? "Added " : "Removed ") + "§e" + itemId + "§f to " + colorName + " " + listType));
        return 1;
    }

    private static int executeFilterToggle(CommandContext<FabricClientCommandSource> context) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(context, "color").toUpperCase());
        String listType = StringArgumentType.getString(context, "listType");
        TrackerFilter filter = TrackersManager.filters.get(color);

        if (listType.equalsIgnoreCase("white")) {
            filter.toggleWhiteList();
            context.getSource().sendFeedback(Component.literal("§a[Filter] §fWhiteList for " + color.name() + ": " + (filter.enableWhiteList ? "§eON" : "§7OFF")));
        } else {
            filter.toggleBlackList();
            context.getSource().sendFeedback(Component.literal("§a[Filter] §fBlackList for " + color.name() + ": " + (filter.enableBlackList ? "§eON" : "§7OFF")));
        }
        return 1;
    }

    private static int executeFilterClear(CommandContext<FabricClientCommandSource> context) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(context, "color").toUpperCase());
        String listType = StringArgumentType.getString(context, "listType");
        TrackerFilter filter = TrackersManager.filters.get(color);
        if (listType.equalsIgnoreCase("white")) filter.clearWhiteList(); else filter.clearBlackList();
        context.getSource().sendFeedback(Component.literal("§c[Filter] Cleared " + color.name() + " " + listType + " list"));
        return 1;
    }

    private static int executeFilterList(CommandContext<FabricClientCommandSource> context) {
        String colorName = StringArgumentType.getString(context, "color");
        String listType = StringArgumentType.getString(context, "listType");
        TrackerColor color = TrackerColor.valueOf(colorName.toUpperCase());
        TrackerFilter filter = TrackersManager.filters.get(color);
        List<String> list = listType.equalsIgnoreCase("white") ? filter.whiteList : filter.blackList;

        context.getSource().sendFeedback(Component.literal("§6--- " + colorName + " " + listType.toUpperCase() + " LIST ---"));
        if (list.isEmpty()) {
            context.getSource().sendFeedback(Component.literal(" §8(Empty)"));
        } else {
            for (String id : list) {
                MutableComponent feedbackText = Component.literal(" §7- §f" + id).withStyle(s -> s
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to remove")))
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
        ctx.getSource().sendFeedback(Component.literal("§a[Counter] §f" + color.name() + " is now " + (counter.isEnable() ? "§eENABLED" : "§7DISABLED")));
        return 1;
    }

    private static int executeCounterReset(CommandContext<FabricClientCommandSource> ctx) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(ctx, "color").toUpperCase());
        TrackersManager.getCounter(color).reset();
        ctx.getSource().sendFeedback(Component.literal("§e[Counter] §fReset " + color.name()));
        return 1;
    }

    private static int executeCounterPrint(CommandContext<FabricClientCommandSource> ctx) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(ctx, "color").toUpperCase());
        if (Minecraft.getInstance().player != null) {
            TrackersManager.getCounter(color).printCounterReport(Minecraft.getInstance().player);
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
        context.getSource().sendFeedback(Component.literal("§a[Point] §fAdded ")
                .append(Component.literal(color.name()).withStyle(s -> s.withColor(color.getHex())))
                .append(" at " + pos.toShortString()));
        return 1;
    }

    private static int executePointAddLook(CommandContext<FabricClientCommandSource> context) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(context, "color").toUpperCase());

        HitResult hit = Minecraft.getInstance().hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            TrackerPointsManager.getInstance().addPoint(color, pos);
            context.getSource().sendFeedback(Component.literal("§a[Point] §fAdded ")
                    .append(Component.literal(color.name()).withStyle(s -> s.withColor(color.getHex())))
                    .append(" at §e" + pos.toShortString() + " §8(Look)"));
        } else {
            context.getSource().sendError(Component.literal("§cYou are not looking at a block!"));
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
                context.getSource().sendFeedback(Component.literal("§e[Point] §fRemoved point at " + pos.toShortString()));
            }
        } catch (Exception e) {
            context.getSource().sendError(Component.literal("§cInvalid coordinates format! Use 'x y z'"));
        }
        return 1;
    }

    private static int executePointRemoveColor(CommandContext<FabricClientCommandSource> ctx) {
        TrackerColor color = TrackerColor.valueOf(StringArgumentType.getString(ctx, "color").toUpperCase());
        TrackerPointsManager.getInstance().removePoint(color);
        ctx.getSource().sendFeedback(Component.literal("§e[Point] §fRemoved all points with color ")
                .append(Component.literal(color.name()).withStyle(s -> s.withColor(color.getHex()))));
        return 1;
    }

    private static int executePointClear(CommandContext<FabricClientCommandSource> ctx) {
        TrackerPointsManager.getInstance().clearAllPoints();
        ctx.getSource().sendFeedback(Component.literal("§c[Point] Cleared all tracking points"));
        return 1;
    }

    private static int executePointList(CommandContext<FabricClientCommandSource> ctx) {
        var points = TrackerPointsManager.getPoints();
        ctx.getSource().sendFeedback(Component.literal("§6--- TRACKING POINTS ---"));

        if (points.isEmpty()) {
            ctx.getSource().sendFeedback(Component.literal(" §8(Empty)"));
        } else {
            points.forEach((pos, state) -> {
                Component posText = Component.literal("[" + pos.toShortString() + "]")
                        .withStyle(style -> style
                                .withColor(state.getColor().getHex())
                                // 修正：使用 ClickEvent.SuggestCommand 记录类
                                .withClickEvent(new ClickEvent.SuggestCommand(
                                        "/tp @s " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                                ))
                                // 修正：使用 HoverEvent.ShowText 记录类
                                .withHoverEvent(new HoverEvent.ShowText(
                                        Component.literal("Click to prepare TP command")
                                ))
                        );

                Component finalFeedback = Component.literal(" §7- ")
                        .append(Component.literal(state.getColor().name() + ": ")
                                .withStyle(s -> s.withColor(state.getColor().getHex())))
                        .append(posText);
                ctx.getSource().sendFeedback(finalFeedback);
            });
        }
        return 1;
    }

}
