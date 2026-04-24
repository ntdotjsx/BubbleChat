package dev.bubblechat.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Folia-safe scheduler utility.
 *
 * Provides a unified API for scheduling tasks on both:
 *   - Folia  → RegionScheduler / EntityScheduler / GlobalRegionScheduler
 *   - Paper/Spigot → BukkitScheduler
 *
 * IMPORTANT for Folia:
 *   - Never modify world state from the global scheduler.
 *   - Always use runAtEntity / runAtLocation for entity/block operations.
 *   - Entity operations MUST go through EntityScheduler.
 */
public final class FoliaUtil {

    private static final boolean FOLIA;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException ignored) {}
        FOLIA = folia;
    }

    private FoliaUtil() {}

    /** Returns true if the server is running Folia. */
    public static boolean isFolia() {
        return FOLIA;
    }

    // ─────────────────────────────────────────────
    //  Run at entity (for entity modifications)
    // ─────────────────────────────────────────────

    /**
     * Run a task on the scheduler that owns the given entity.
     * On Folia this uses EntityScheduler; on Paper/Spigot uses main thread via BukkitScheduler.
     */
    public static void runAtEntity(Plugin plugin, Entity entity, Runnable task) {
        if (FOLIA) {
            entity.getScheduler().run(plugin, scheduled -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run a delayed task on the scheduler owning the entity.
     * @param delayTicks delay in ticks
     */
    public static void runAtEntityDelayed(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (FOLIA) {
            entity.getScheduler().runDelayed(plugin, scheduled -> task.run(), null, delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    // ─────────────────────────────────────────────
    //  Run at location (for world/region operations)
    // ─────────────────────────────────────────────

    /**
     * Run a task on the region scheduler for the given location.
     */
    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().run(plugin, location, scheduled -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run a delayed task for the given location's region.
     */
    public static void runAtLocationDelayed(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduled -> task.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    /**
     * Run a repeating task for the given location's region.
     */
    public static void runAtLocationRepeating(Plugin plugin, Location location, Runnable task,
                                              long initialDelay, long period) {
        if (FOLIA) {
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, location,
                    scheduled -> task.run(), initialDelay, period);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelay, period);
        }
    }

    // ─────────────────────────────────────────────
    //  Global async (safe for I/O only, NO world state)
    // ─────────────────────────────────────────────

    /**
     * Run a task asynchronously (safe for config/file I/O only).
     * Do NOT modify world state here.
     */
    public static void runAsync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, scheduled -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Run a delayed async task.
     */
    public static void runAsyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (FOLIA) {
            // Folia async uses time-based delay
            long millis = delayTicks * 50L;
            Bukkit.getAsyncScheduler().runDelayed(plugin, scheduled -> task.run(), millis, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    // ─────────────────────────────────────────────
    //  Global region scheduler (for global tasks)
    // ─────────────────────────────────────────────

    /**
     * Run a task on the global region scheduler.
     * On Folia this is safe only for tasks that don't touch a specific region.
     */
    public static void runGlobal(Plugin plugin, Runnable task) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, scheduled -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run a repeating global task.
     */
    public static void runGlobalRepeating(Plugin plugin, Runnable task, long initialDelay, long period) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, scheduled -> task.run(), initialDelay, period);
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelay, period);
        }
    }
}