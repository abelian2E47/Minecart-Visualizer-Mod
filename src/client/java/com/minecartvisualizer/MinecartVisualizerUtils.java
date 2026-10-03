package com.minecartvisualizer;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;


public class MinecartVisualizerUtils {

    public static boolean isEntityLoaded(UUID uuid) {

        if (Minecraft.getInstance().level == null) {return false;}
        Iterable<Entity> allEntities = Minecraft.getInstance().level.entitiesForRendering();
        if (allEntities == null){return false;}
        List<UUID> entitiesUUID = new ArrayList<>();
        for (Entity allEntity : allEntities) {
            UUID entityUuid = allEntity.getUUID();
            entitiesUUID.add(entityUuid);
        }
        return entitiesUUID.contains(uuid);
    }

    public static String getMovementDirection(Vec3 velocity) {
        double horizontalSpeedSq = velocity.x * velocity.x + velocity.z * velocity.z;

        if (horizontalSpeedSq == 0) {
            return "stop";
        }

        Direction dir = Direction.getApproximateNearest(velocity.x, 0, velocity.z);

        return dir.toString();
    }
}
