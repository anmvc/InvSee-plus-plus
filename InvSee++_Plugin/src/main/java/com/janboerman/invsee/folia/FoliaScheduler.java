package com.janboerman.invsee.folia;

import com.janboerman.invsee.spigot.InvseePlusPlus;
import com.janboerman.invsee.spigot.api.Scheduler;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler implementation based on {@link io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler},
 * {@link io.papermc.paper.threadedregions.scheduler.AsyncScheduler} and {@link EntityScheduler}.
 */
public class FoliaScheduler implements Scheduler {

    private final InvseePlusPlus plugin;

    public FoliaScheduler(InvseePlusPlus plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isFolia() {
        return true;
    }

    @Override
    public void executeSyncPlayer(UUID playerId, Runnable task, Runnable retired) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            executeSyncPlayer(player, task, retired);
        } else {
            //the player is not online: their player data is not owned by any region, so the global region will do.
            executeSyncGlobal(task);
        }
    }

    @Override
    public void executeSyncPlayer(HumanEntity player, Runnable task, Runnable retired) {
        //null is not accepted by all versions of Folia, so we provide a sensible default:
        //when the entity is retired, its data is not owned by any region anymore either.
        final Runnable onRetire = retired != null ? retired : task;
        player.getScheduler().run(plugin, scheduledTask -> task.run(), () -> executeSyncGlobal(onRetire));
    }

    @Override
    public void executeSyncGlobal(Runnable task) {
        plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
    }

    @Override
    public void executeSyncGlobalRepeatedly(Runnable task, long ticksInitialDelay, long ticksPeriod) {
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), ticksInitialDelay, ticksPeriod);
    }

    @Override
    public void executeAsync(Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    @Override
    public void executeLaterGlobal(Runnable task, long delayTicks) {
        plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
    }

    @Override
    public void executeLaterAsync(Runnable task, long delayTicks) {
        plugin.getServer().getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks * 50, TimeUnit.MILLISECONDS);
    }

}
