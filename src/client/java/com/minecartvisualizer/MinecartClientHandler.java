package com.minecartvisualizer;

import com.minecartvisualizer.config.MinecartVisualizerConfig;
import com.minecartvisualizer.mixin.client.ClientWorldAccessor;
import com.minecartvisualizer.tracker.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class MinecartClientHandler {

    private static final Map<UUID, MinecartDataPayload> MINECART_DATA = new ConcurrentHashMap<>();
    private static final Map<UUID, HopperMinecartDataPayload> HOPPER_MINECART_DATA = new ConcurrentHashMap<>();
    private static final Map<UUID, TNTMinecartDataPayload> TNT_MINECART_DATA = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> minecarts = new ConcurrentHashMap<>();
    private static final Map<UUID, MinecartsGroup> uuidToGroup = new HashMap<>();
    private static final Set<UUID> currentLeaders = new HashSet<>();

    public static void updateMinecartGroups() {
        List<MinecartDataPayload> activeCarts = new ArrayList<>();
        for (UUID uuid : minecarts.keySet()) {
            if (MinecartVisualizerUtils.isEntityLoaded(uuid)) {
                MinecartDataPayload data = getMinecartData(uuid);
                if (data != null) {
                    activeCarts.add(data);
                }
            }
        }

        if (activeCarts.isEmpty()) return;

        //根据x坐标排序
        activeCarts.sort(Comparator.comparingDouble(d -> d.pos().x));

        List<MinecartsGroup> finalGroups = new ArrayList<>();
        int size = activeCarts.size();
        boolean[] merged = new boolean[size];

        final double threshold = 0.5;

        for (int i = 0; i < size; i++) {
            if (merged[i]) continue;

            MinecartDataPayload root = activeCarts.get(i);
            MinecartsGroup group = new MinecartsGroup(root.uuid());
            merged[i] = true;

            Vec3d rootPos = root.pos();

            for (int j = i + 1; j < size; j++) {
                if (merged[j]) continue;

                MinecartDataPayload candidate = activeCarts.get(j);
                Vec3d candidatePos = candidate.pos();
                //x轴差距大于0.5直接跳过后续匹配
                double dx = candidatePos.x - rootPos.x;
                if (dx > threshold) break;

                if (Math.abs(candidatePos.y - rootPos.y) > threshold) continue;
                if (Math.abs(candidatePos.z - rootPos.z) > threshold) continue;

                group.addMinecart(candidate.uuid());
                merged[j] = true;
            }
            finalGroups.add(group);
        }

        for (MinecartsGroup group : finalGroups) {
            group.sort();
        }
        rebuildCache(finalGroups);
    }

    public static void trackMinecartByPoint() {
        Map<BlockPos, PointState> triggerPoints = TrackerPointsManager.getPoints();
        if (triggerPoints.isEmpty()) return;

        Set<BlockPos> activePositions = new HashSet<>();
        List<MinecartDataPayload> activeCarts = new ArrayList<>();

        for (UUID uuid : minecarts.keySet()) {
            if (MinecartVisualizerUtils.isEntityLoaded(uuid)) {
                MinecartDataPayload data = getMinecartData(uuid);
                if (data != null) {
                    activeCarts.add(data);
                    activePositions.add(BlockPos.ofFloored(data.pos().x, data.pos().y, data.pos().z));
                }
            }
        }

        triggerPoints.forEach((pos, state) -> state.setActive(activePositions.contains(pos)));

        if (activeCarts.isEmpty()) return;

        for (MinecartDataPayload cart : activeCarts) {
            BlockPos cartBlockPos = BlockPos.ofFloored(cart.pos().x, cart.pos().y, cart.pos().z);
            PointState state = triggerPoints.get(cartBlockPos);

            if (state != null) {
                TrackerColor color = state.getColor();
                HopperMinecartTracker currentTracker = TrackersManager.getTracker(cart.uuid());

                if (currentTracker == null || currentTracker.getTrackerColor() != color) {
                    TrackersManager.setTracker(cart.uuid(), cart.id(), color);
                }
            }
        }
    }

    private static void rebuildCache(Collection<MinecartsGroup> finalGroups) {
        uuidToGroup.clear();
        currentLeaders.clear();

        for (MinecartsGroup group : finalGroups) {
            currentLeaders.add(group.getLeader());

            for (UUID member : group.getMinecarts()) {
                uuidToGroup.put(member, group);
            }
        }
    }


    public static int getGroupSize(UUID uuid) {
        MinecartsGroup group = uuidToGroup.get(uuid);
        if (group != null) {
            return group.getSize();
        }
        return 1;
    }

    public static boolean isLeader(UUID uuid) {
        return currentLeaders.contains(uuid);
    }

    public static MinecartsGroup getGroup(UUID uuid) {
        return uuidToGroup.get(uuid);
    }

    //获取最先运算的漏斗矿车
    public static UUID getPriority(MinecartsGroup group) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || group.getMinecarts().isEmpty()) return null;

        var entityManager = ((ClientWorldAccessor) client.world).getEntityManager();

        List<Entity> hopperMinecarts = new ArrayList<>();

        for (UUID uuid : group.getMinecarts()) {
            Entity entity = entityManager.getLookup().get(uuid);
            if (entity != null) {
                if (entity instanceof net.minecraft.entity.vehicle.HopperMinecartEntity) {
                    hopperMinecarts.add(entity);
                }
            }
        }

        if (hopperMinecarts.isEmpty()) {
            return null;
        }

        hopperMinecarts.sort(Comparator.comparingInt(Entity::getId));

        return hopperMinecarts.getFirst().getUuid();
    }

    public static void register() {
        var config = MinecartVisualizerConfig.getInstance();

        ClientPlayNetworking.registerGlobalReceiver(MinecartDataPayload.ID, (payload, context) -> MinecraftClient.getInstance().execute(() -> {
            MINECART_DATA.put(payload.uuid(), payload);
            minecarts.put(payload.uuid(),BlockPos.ofFloored(payload.pos()));
        }));

        ClientPlayNetworking.registerGlobalReceiver(HopperMinecartDataPayload.ID,
                (payload, context) -> MinecraftClient.getInstance().execute(() -> HOPPER_MINECART_DATA.put(payload.uuid(), payload)));

        ClientPlayNetworking.registerGlobalReceiver(TNTMinecartDataPayload.ID,
                (payload, context) -> MinecraftClient.getInstance().execute(() -> {TNT_MINECART_DATA.put(payload.uuid(), payload);
                if (payload.isExploded() && config.trackTNTMinecart){
                    ClientPlayerEntity player = MinecraftClient.getInstance().player;
                    Text headText = Text.literal("[Exploded]").setStyle(Style.EMPTY.withColor(0x8FBF3A));
                    Text posText = Text.literal("At" + payload.explosionPos().toString()).setStyle(Style.EMPTY.withColor(0xDE2E6E));
                    Text message = headText.copy().append(posText);
                    if (player != null){player.sendMessage(message, false);}
                }
                }));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null) {
                if (client.world.getTime() % 20 == 0) {
                    cleanUpData();
                }
            }

            if (config.mergeStackingMinecartInfo){
                if (client.world != null) {
                    if (client.world.getTime() % 5 == 0){
                        updateMinecartGroups();
                    }
                }
            }

            if (!TrackerPointsManager.getPoints().isEmpty()){
                trackMinecartByPoint();
            }
        });
    }


    public static MinecartDataPayload getMinecartData(UUID uuid) {
        return MINECART_DATA.get(uuid);
    }

    public static HopperMinecartDataPayload getHopperMinecartData(UUID uuid) {
        return HOPPER_MINECART_DATA.get(uuid);
    }

    public static TNTMinecartDataPayload getTNTMinecartData(UUID uuid) {
        return TNT_MINECART_DATA.get(uuid);
    }

    private static void cleanUpData() {
        MINECART_DATA.keySet().removeIf(uuid -> !MinecartVisualizerUtils.isEntityLoaded(uuid));
        HOPPER_MINECART_DATA.keySet().removeIf(uuid -> !MinecartVisualizerUtils.isEntityLoaded(uuid));
        TNT_MINECART_DATA.keySet().removeIf(uuid -> !MinecartVisualizerUtils.isEntityLoaded(uuid));
        minecarts.keySet().removeIf(uuid -> !MinecartVisualizerUtils.isEntityLoaded(uuid));
    }
}