package com.minecartvisualizer;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;


public class MinecartVisualizerUtils {

    public static boolean isEntityLoaded(UUID uuid) {

        if (MinecraftClient.getInstance().world == null) {return false;}
        Iterable<Entity> allEntities = MinecraftClient.getInstance().world.getEntities();
        if (allEntities == null){return false;}
        List<UUID> entitiesUUID = new ArrayList<>();
        for (Entity allEntity : allEntities) {
            UUID entityUuid = allEntity.getUuid();
            entitiesUUID.add(entityUuid);
        }
        return entitiesUUID.contains(uuid);
    }

    public static String getMovementDirection(Vec3d velocity) {
        double horizontalSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;

        if (horizontalSpeedSq == 0) {
            return "stop";
        }

        Direction dir = Direction.getFacing(velocity.x, 0, velocity.z);

        return dir.toString();
    }
}
