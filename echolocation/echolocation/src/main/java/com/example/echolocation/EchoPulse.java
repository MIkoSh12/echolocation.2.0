package com.example.echolocation;

import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Одна "волна" эха, рождённая звуком. */
public class EchoPulse {
    public static final int WAVE_TICKS = 24;   // за сколько тиков волна достигает макс. радиуса
    public static final int LIFE_TICKS = 70;   // общее время жизни (подсветка угасает)

    /** Поверхностный блок на фронте волны; mask — битовая маска граней, смотрящих в воздух. */
    public record ShellBlock(int x, int y, int z, int mask) {}

    /** Бокс моба внутри волны (мировые координаты). */
    public record MobBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {}

    public final Vec3 origin;
    public final double maxRadius;
    public int age = 0;

    // Списки только заменяются целиком (никогда не меняются на месте) — безопасно отдавать рендеру.
    public volatile List<ShellBlock> shell = List.of();
    public volatile List<MobBox> mobs = List.of();

    public EchoPulse(Vec3 origin, double maxRadius) {
        this.origin = origin;
        this.maxRadius = maxRadius;
    }

    public double waveRadius() {
        return maxRadius * Math.min(1.0, age / (double) WAVE_TICKS);
    }

    public boolean isDead() {
        return age > LIFE_TICKS;
    }

    /** 1.0 -> 0.0 по мере угасания. */
    public float fade() {
        return Math.max(0f, 1f - age / (float) LIFE_TICKS);
    }
}
