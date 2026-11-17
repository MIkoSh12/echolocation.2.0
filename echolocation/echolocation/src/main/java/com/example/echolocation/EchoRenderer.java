package com.example.echolocation;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Рисует контуры эха через встроенный в игру Gizmos API.
 * setAlwaysOnTop() => линии видны сквозь стены.
 * Так мод не зависит от низкоуровневого рендера, который в 26.3 был полностью переписан.
 */
public final class EchoRenderer {
    /** Максимум контуров за кадр (защита от просадки FPS). */
    private static final int MAX_GIZMOS = 2500;

    private EchoRenderer() {}

    public static void register() {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> draw());
    }

    private static void draw() {
        List<EchoManager.Layer> layers = EchoManager.snapshot();
        if (layers.isEmpty()) return;

        int budget = MAX_GIZMOS;
        for (EchoManager.Layer layer : layers) {
            // Блоки на фронте волны: голубые контуры.
            GizmoStyle blockStyle = GizmoStyle.stroke(argb(0.35f + 0.65f * layer.fade(), 51, 217, 255));
            for (EchoPulse.ShellBlock b : layer.shell()) {
                if (budget-- <= 0) return;
                Gizmos.cuboid(new AABB(b.x(), b.y(), b.z(), b.x() + 1, b.y() + 1, b.z() + 1), blockStyle)
                        .setAlwaysOnTop();
            }
            // Мобы внутри волны: оранжевые контуры.
            GizmoStyle mobStyle = GizmoStyle.stroke(argb(0.5f + 0.5f * layer.fade(), 255, 140, 25));
            for (EchoPulse.MobBox m : layer.mobs()) {
                if (budget-- <= 0) return;
                Gizmos.cuboid(new AABB(m.minX(), m.minY(), m.minZ(), m.maxX(), m.maxY(), m.maxZ()), mobStyle)
                        .setAlwaysOnTop();
            }
        }
    }

    private static int argb(float alpha, int r, int g, int b) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
