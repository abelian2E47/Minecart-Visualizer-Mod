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
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;


/**
 * 客户端保存的服务端权威数据。
 *
 * <p>所有显示都以这里的数据为准：数据本身由服务端每 tick 下发，并带上服务端时间戳；
 * 客户端只用它做插值显示与时效判定，不再拿客户端自己的世界状态去推算矿车的状态。</p>
 */
public class MinecartClientHandler {

    /** 数据超过这么多毫秒没有更新就视为失效（不再显示，也不参与统计）。 */
    public static final long MAX_FRESH_AGE_MS = 2000L;
    /** 追踪中的矿车失去同步超过这么多毫秒后静默放弃（不算被摧毁）。 */
    public static final long LOST_CONTACT_MS = 60000L;
    /** 同一台矿车最多缓存多少条碰撞事件（没有追踪器消费时不会无限增长）。 */
    private static final int MAX_COLLISION_NOTICES = 32;

    private static final Map<UUID, MinecartDataPayload> MINECART_DATA = new ConcurrentHashMap<>();
    private static final Map<UUID, HopperMinecartDataPayload> HOPPER_MINECART_DATA = new ConcurrentHashMap<>();
    private static final Map<UUID, TNTMinecartDataPayload> TNT_MINECART_DATA = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> minecarts = new ConcurrentHashMap<>();
    //每个矿车最近一次收到服务端数据的时间（毫秒，仅用于判断数据是否过期，不参与显示）
    private static final Map<UUID, Long> LAST_UPDATE_MS = new ConcurrentHashMap<>();
    //服务端权威坐标：最近一次与上一次同步到的矿车坐标，用于在两个服务端 tick 之间插值
    private static final Map<UUID, Vec3d> SERVER_POS = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3d> SERVER_PREV_POS = new ConcurrentHashMap<>();
    //由服务端销毁通知留下的最后一份数据（含最终坐标与最终物品栏），供追踪器出报告用
    private static final Map<UUID, RemovalNotice> REMOVED_DATA = new ConcurrentHashMap<>();
    //服务端下发的"被挤压"事件，按矿车排队，由追踪器逐条取用后输出到聊天栏
    private static final Map<UUID, Deque<MinecartCollisionPayload>> COLLISION_NOTICES = new ConcurrentHashMap<>();
    private static volatile long latestServerTime = -1L;
    private static volatile Map<UUID, MinecartsGroup> uuidToGroup = new ConcurrentHashMap<>();
    private static volatile Set<UUID> currentLeaders = ConcurrentHashMap.newKeySet();

    /**
     * 服务端销毁通知：{@code data} 是销毁瞬间的矿车状态（{@code removed=true}），
     * {@code hopper} 是同一时刻的漏斗矿车快照（若该矿车是漏斗矿车）。
     */
    public record RemovalNotice(MinecartDataPayload data, HopperMinecartDataPayload hopper) {
    }

    public static void updateMinecartGroups() {
        List<MinecartDataPayload> activeCarts = new ArrayList<>();
        for (UUID uuid : minecarts.keySet()) {
            MinecartDataPayload data = getFreshMinecartData(uuid);
            if (data != null) {
                activeCarts.add(data);
            }
        }

        if (activeCarts.isEmpty()) {
            rebuildCache(List.of());
            return;
        }

        //根据服务端广播的x坐标排序
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

        //组内顺序按服务端 x 坐标（和分组时的排序一致），领队＝组内第一台，
        //这样"物品栏取第一台的数据"和"文字/框体只画领队"指的是同一台矿车
        Map<UUID, Double> posX = new HashMap<>();
        for (MinecartDataPayload data : activeCarts) {
            posX.put(data.uuid(), data.pos().x);
        }
        for (MinecartsGroup group : finalGroups) {
            group.sort(Comparator.comparingDouble(uuid -> posX.getOrDefault(uuid, 0.0)));
        }
        rebuildCache(finalGroups);
    }

    public static void trackMinecartByPoint() {
        Map<BlockPos, PointState> triggerPoints = TrackerPointsManager.getPoints();
        if (triggerPoints.isEmpty()) return;

        Set<BlockPos> activePositions = new HashSet<>();
        List<MinecartDataPayload> activeCarts = new ArrayList<>();

        for (UUID uuid : minecarts.keySet()) {
            MinecartDataPayload data = getFreshMinecartData(uuid);
            if (data != null) {
                activeCarts.add(data);
                activePositions.add(BlockPos.ofFloored(data.pos().x, data.pos().y, data.pos().z));
            }
        }

        triggerPoints.forEach((pos, state) -> {
            state.setActive(activePositions.contains(pos));
        });

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
        Map<UUID, MinecartsGroup> newUuidToGroup = new ConcurrentHashMap<>();
        Set<UUID> newCurrentLeaders = ConcurrentHashMap.newKeySet();

        for (MinecartsGroup group : finalGroups) {
            newCurrentLeaders.add(group.getLeader());

            for (UUID member : group.getMinecarts()) {
                newUuidToGroup.put(member, group);
            }
        }

        uuidToGroup = newUuidToGroup;
        currentLeaders = newCurrentLeaders;
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

    //获取最先运算的漏斗矿车（按服务端下发的实体 id 排序）
    public static UUID getPriority(MinecartsGroup group) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || group == null || group.getMinecarts().isEmpty()) return null;

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

        //1.20.4 最低支持 Java 17，没有 List#getFirst()
        return hopperMinecarts.get(0).getUuid();
    }

    public static void register() {
        var config = MinecartVisualizerConfig.getInstance();

        ClientPlayNetworking.registerGlobalReceiver(MinecartVisualizer.MINECART_DATA_PACKET_ID,
                (client, handler, buf, sender) -> {
                    MinecartDataPayload payload = MinecartDataPayload.read(buf);
                    client.execute(() -> {
                        if (payload.serverTime() > latestServerTime) {
                            latestServerTime = payload.serverTime();
                        }

                        if (payload.removed()) {
                            //服务端说它被销毁了：留下最后一份数据（连同刚到的漏斗矿车快照）给追踪器出报告，
                            //然后清掉实时数据，后面的显示不再引用它
                            REMOVED_DATA.put(payload.uuid(),
                                    new RemovalNotice(payload, HOPPER_MINECART_DATA.get(payload.uuid())));
                            dropLiveData(payload.uuid());
                            return;
                        }

                        MINECART_DATA.put(payload.uuid(), payload);
                        LAST_UPDATE_MS.put(payload.uuid(), Util.getMeasuringTimeMs());
                        minecarts.put(payload.uuid(), BlockPos.ofFloored(payload.pos()));
                        recordServerPos(payload.uuid(), payload.pos());
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(MinecartVisualizer.HOPPER_MINECART_DATA_PACKET_ID,
                (client, handler, buf, sender) -> {
                    HopperMinecartDataPayload payload = HopperMinecartDataPayload.read(buf);
                    client.execute(() -> HOPPER_MINECART_DATA.put(payload.uuid(), payload));
                });

        ClientPlayNetworking.registerGlobalReceiver(MinecartVisualizer.MINECART_COLLISION_PACKET_ID,
                (client, handler, buf, sender) -> {
                    MinecartCollisionPayload payload = MinecartCollisionPayload.read(buf);
                    client.execute(() -> {
                        Deque<MinecartCollisionPayload> queue =
                                COLLISION_NOTICES.computeIfAbsent(payload.uuid(), uuid -> new ConcurrentLinkedDeque<>());
                        queue.addLast(payload);
                        while (queue.size() > MAX_COLLISION_NOTICES) {
                            queue.pollFirst();
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(MinecartVisualizer.TNT_MINECART_DATA_PACKET_ID,
                (client, handler, buf, sender) -> {
                    TNTMinecartDataPayload payload = TNTMinecartDataPayload.read(buf);
                    client.execute(() -> {
                        TNT_MINECART_DATA.put(payload.uuid(), payload);
                        if (payload.isExploded() && config.trackTNTMinecart) {
                            ClientPlayerEntity player = MinecraftClient.getInstance().player;
                            Text headText = Text.literal("[Exploded]").setStyle(Style.EMPTY.withColor(0x8FBF3A));
                            Text posText = Text.literal("At" + payload.explosionPos().toString()).setStyle(Style.EMPTY.withColor(0xDE2E6E));
                            Text message = headText.copy().append(posText);
                            if (player != null) {
                                player.sendMessage(message, false);
                            }
                        }
                    });
                });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;

            cleanUpData();

            if (config.mergeStackingMinecartInfo) {
                //每 tick 重算分组，避免"堆叠数量/合并物品栏"最多滞后 5 tick
                updateMinecartGroups();
            }

            if (!TrackerPointsManager.getPoints().isEmpty()) {
                trackMinecartByPoint();
            }
        });
    }


    public static MinecartDataPayload getMinecartData(UUID uuid) {
        return MINECART_DATA.get(uuid);
    }

    /** 只在该矿车的数据仍然新鲜时返回，避免显示早已过期的内容。 */
    public static MinecartDataPayload getFreshMinecartData(UUID uuid) {
        MinecartDataPayload data = MINECART_DATA.get(uuid);
        if (data == null || !isFresh(uuid)) {
            return null;
        }
        return data;
    }

    public static HopperMinecartDataPayload getHopperMinecartData(UUID uuid) {
        return HOPPER_MINECART_DATA.get(uuid);
    }

    /** 只在该矿车的数据仍然新鲜时返回。 */
    public static HopperMinecartDataPayload getFreshHopperMinecartData(UUID uuid) {
        HopperMinecartDataPayload data = HOPPER_MINECART_DATA.get(uuid);
        if (data == null || !isFresh(uuid)) {
            return null;
        }
        return data;
    }

    public static TNTMinecartDataPayload getTNTMinecartData(UUID uuid) {
        return TNT_MINECART_DATA.get(uuid);
    }

    /** 该矿车的数据是否仍然新鲜（距最近一次收到服务端数据的时间在阈值内）。 */
    public static boolean isFresh(UUID uuid) {
        if (MINECART_DATA.get(uuid) == null) {
            return false;
        }
        Long lastUpdate = LAST_UPDATE_MS.get(uuid);
        return lastUpdate != null && Util.getMeasuringTimeMs() - lastUpdate <= MAX_FRESH_AGE_MS;
    }

    /** 该矿车距最近一次收到服务端数据已经过了多少毫秒（没有数据返回 -1）。 */
    public static long getMillisSinceUpdate(UUID uuid) {
        Long lastUpdate = LAST_UPDATE_MS.get(uuid);
        return lastUpdate == null ? -1L : Util.getMeasuringTimeMs() - lastUpdate;
    }

    /** 最近一次收到的服务端时间，用于追踪器计时与统计（替代客户端自己的 tick 计数）。 */
    public static long getLatestServerTime() {
        return latestServerTime;
    }

    /** 当前单调时钟（毫秒），只用于判断数据是否过期。 */
    public static long nowMs() {
        return Util.getMeasuringTimeMs();
    }

    /** 取走并清除某个矿车的销毁通知；返回服务端补发的最后一份数据。 */
    public static RemovalNotice consumeRemoval(UUID uuid) {
        return REMOVED_DATA.remove(uuid);
    }

    /**
     * 取走该矿车最早的一条"被挤压"事件；没有则返回 {@code null}。
     *
     * <p>只有真的在追踪这台矿车（存在追踪器）时才有人来取，所以"没被追踪就不输出"
     * 是天然成立的，不需要额外判断。</p>
     */
    public static MinecartCollisionPayload pollCollision(UUID uuid) {
        Deque<MinecartCollisionPayload> queue = COLLISION_NOTICES.get(uuid);
        return queue == null ? null : queue.pollFirst();
    }

    public static void recordServerPos(UUID uuid, Vec3d pos) {
        if (pos == null) return;

        Vec3d previous = SERVER_POS.get(uuid);
        if (previous != null && !previous.equals(pos)) {
            SERVER_PREV_POS.put(uuid, previous);
        }
        SERVER_POS.put(uuid, pos);
    }

    /**
     * 取矿车的服务端权威坐标，并在最近两次服务端同步之间按渲染 tick 插值，
     * 避免网络抖动导致的框体跳动。
     *
     * @return 未收到服务端坐标时返回 {@code null}
     */
    public static Vec3d getServerPos(UUID uuid, float tickDelta) {
        Vec3d current = SERVER_POS.get(uuid);
        if (current == null) {
            return null;
        }

        Vec3d previous = SERVER_PREV_POS.get(uuid);
        if (previous == null) {
            return current;
        }

        return previous.lerp(current, MathHelper.clamp(tickDelta, 0.0f, 1.0f));
    }

    private static void dropLiveData(UUID uuid) {
        MINECART_DATA.remove(uuid);
        HOPPER_MINECART_DATA.remove(uuid);
        TNT_MINECART_DATA.remove(uuid);
        minecarts.remove(uuid);
        LAST_UPDATE_MS.remove(uuid);
        SERVER_POS.remove(uuid);
        SERVER_PREV_POS.remove(uuid);
    }

    private static void cleanUpData() {
        //失效判定依据"多久没收到服务端数据"，而不是客户端实体列表：
        //走出下发距离后服务端不再广播，客户端实体却可能还在，旧代码因此会一直显示过期的内容
        for (UUID uuid : new ArrayList<>(MINECART_DATA.keySet())) {
            if (!isFresh(uuid)) {
                dropLiveData(uuid);
            }
        }
        HOPPER_MINECART_DATA.keySet().removeIf(uuid -> !MINECART_DATA.containsKey(uuid));
        TNT_MINECART_DATA.keySet().removeIf(uuid -> !MINECART_DATA.containsKey(uuid));
        minecarts.keySet().removeIf(uuid -> !MINECART_DATA.containsKey(uuid));
        LAST_UPDATE_MS.keySet().removeIf(uuid -> !MINECART_DATA.containsKey(uuid));
        SERVER_POS.keySet().removeIf(uuid -> !MINECART_DATA.containsKey(uuid));
        SERVER_PREV_POS.keySet().removeIf(uuid -> !MINECART_DATA.containsKey(uuid));
        //碰撞事件按矿车排队：没人取（没被追踪）就随时间清掉，避免无限增长
        COLLISION_NOTICES.keySet().removeIf(uuid -> !MINECART_DATA.containsKey(uuid));
        //销毁通知留一段时间给追踪器取用，之后丢弃，避免无限增长
        REMOVED_DATA.entrySet().removeIf(entry -> !isPending(entry.getKey()));
    }

    /** 销毁通知是否还没被追踪器取走（超过一定时间没有追踪器认领就丢掉）。 */
    private static boolean isPending(UUID uuid) {
        return TrackersManager.containsTracker(uuid);
    }

    public static void clearAll() {
        MINECART_DATA.clear();
        HOPPER_MINECART_DATA.clear();
        TNT_MINECART_DATA.clear();
        minecarts.clear();
        LAST_UPDATE_MS.clear();
        SERVER_POS.clear();
        SERVER_PREV_POS.clear();
        REMOVED_DATA.clear();
        COLLISION_NOTICES.clear();
        latestServerTime = -1L;
        uuidToGroup.clear();
        currentLeaders.clear();
    }
}
