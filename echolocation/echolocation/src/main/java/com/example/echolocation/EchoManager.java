package com.example.echolocation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class EchoManager {
    public static final int MAX_PULSES = 8;
    public static final double MAX_RADIUS = 20.0;
    /** Толщина фронта волны в блоках. */
    private static final double BAND = 1.6;
    /** Звуки тише этого порога игнорируем. */
    private static final float MIN_VOLUME = 0.15f;
    private static final int MAX_MOBS_PER_PULSE = 48;

    private static final List<EchoPulse> PULSES = new ArrayList<>();

    private EchoManager() {}

    /** Снимок для рендера. */
    public record Layer(List<EchoPulse.ShellBlock> shell, List<EchoPulse.MobBox> mobs, float fade) {}

    public static void clear() { PULSES.clear(); }

    /** Вызывается из миксина при воспроизведении звука. */
    public static void onSound(double x, double y, double z, float volume) {
        if (volume < MIN_VOLUME) return;
        double radius = Math.min(MAX_RADIUS, 5.0 + volume * 9.0);
        synchronized (PULSES) {
            PULSES.add(new EchoPulse(new Vec3(x, y, z), radius));
            while (PULSES.size() > MAX_PULSES) PULSES.remove(0);
        }
    }

    public static void tick(ClientLevel level) {
        synchronized (PULSES) {
            Iterator<EchoPulse> it = PULSES.iterator();
            while (it.hasNext()) {
                EchoPulse p = it.next();
                p.age++;
                if (p.isDead()) { it.remove(); continue; }
                updateShell(level, p);
                updateMobs(level, p);
            }
        }
    }

    /** Берётся на этапе extraction. Возвращает неизменяемые данные. */
    public static List<Layer> snapshot() {
        synchronized (PULSES) {
            List<Layer> out = new ArrayList<>(PULSES.size());
            for (EchoPulse p : PULSES) out.add(new Layer(p.shell, p.mobs, p.fade()));
            return out;
        }
    }

    private static void updateShell(ClientLevel level, EchoPulse p) {
        if (p.age > EchoPulse.WAVE_TICKS + 6) { // фронт уже ушёл
            p.shell = List.of();
            return;
        }
        double r = p.waveRadius();
        double inner = Math.max(0, r - BAND);
        double inner2 = inner * inner, outer2 = r * r;
        int ir = (int) Math.ceil(r);
        BlockPos c = BlockPos.containing(p.origin.x, p.origin.y, p.origin.z);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        Direction[] dirs = Direction.values();
        List<EchoPulse.ShellBlock> out = new ArrayList<>();

        for (int dx = -ir; dx <= ir; dx++) {
            for (int dy = -ir; dy <= ir; dy++) {
                for (int dz = -ir; dz <= ir; dz++) {
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 < inner2 || d2 > outer2) continue;
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (level.getBlockState(m).isAir()) continue;
                    int mask = 0;
                    for (Direction dir : dirs) {
                        n.set(m).move(dir);
                        if (level.getBlockState(n).isAir()) mask |= 1 << dir.ordinal();
                    }
                    if (mask != 0) out.add(new EchoPulse.ShellBlock(m.getX(), m.getY(), m.getZ(), mask));
                }
            }
        }
        p.shell = out;
    }

    private static void updateMobs(ClientLevel level, EchoPulse p) {
        double r = p.waveRadius();
        AABB area = new AABB(
                p.origin.x - r, p.origin.y - r, p.origin.z - r,
                p.origin.x + r, p.origin.y + r, p.origin.z + r);
        Entity self = Minecraft.getInstance().player;
        List<Entity> found = level.getEntities((Entity) null, area, e -> e instanceof LivingEntity && e != self);

        List<EchoPulse.MobBox> out = new ArrayList<>();
        for (Entity e : found) {
            AABB b = e.getBoundingBox();
            if (b.getCenter().distanceTo(p.origin) > r) continue;
            out.add(new EchoPulse.MobBox(
                    (float) b.minX, (float) b.minY, (float) b.minZ,
                    (float) b.maxX, (float) b.maxY, (float) b.maxZ));
            if (out.size() >= MAX_MOBS_PER_PULSE) break;
        }
        p.mobs = out;
    }
}
