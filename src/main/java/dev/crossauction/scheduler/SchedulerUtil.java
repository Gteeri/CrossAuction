package dev.crossauction.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

/**
 * Wraps Paper's region-aware scheduler APIs so the plugin behaves correctly
 * on BOTH regular Paper and Folia without any if-checks: these scheduler
 * APIs exist on Paper too (as a compatibility shim that just behaves like
 * the normal Bukkit scheduler there), and are REQUIRED on Folia because
 * regular Bukkit scheduler calls throw there.
 * See: https://docs.papermc.io/paper/dev/folia-support/
 */
public final class SchedulerUtil {

    private SchedulerUtil() {}

    /** Off-thread work: DB/Redis calls, heavy computation. Safe on Paper + Folia. */
    public static void runAsync(Plugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> task.run());
    }

    public static void runAsyncRepeating(Plugin plugin, Runnable task, long initialDelaySeconds, long periodSeconds) {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> task.run(),
                Math.max(1, initialDelaySeconds), Math.max(1, periodSeconds), TimeUnit.SECONDS);
    }

    /** Work that must run on the region thread owning a specific entity (e.g. opening an inventory, sending a message). */
    public static void runForEntity(Plugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, t -> task.run(), null);
    }

    /** Work that isn't tied to any single entity/region (e.g. global bookkeeping). */
    public static void runGlobalRegion(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
    }
}
