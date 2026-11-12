package com.janboerman.invsee.spigot.api;

import java.util.UUID;

import javax.annotation.Nullable;

import org.bukkit.entity.HumanEntity;
import org.bukkit.plugin.Plugin;

/**
 * Scheduler abstraction for scheduling tasks.
 *
 * @see <a href="https://github.com/Jannyboy11/InvSee-plus-plus/wiki/Folia-support">Folia support</a>
 */
public interface Scheduler {

    /**
     * Get whether this scheduler schedules tasks on a server which uses threaded regions (Folia).
     * <p>
     * On such servers, the data of an entity may only be accessed by the thread which owns the region the entity is in.
     * This means that a player's inventory must never be read from or written to by another player's thread.
     * Implementations which return {@code true} must therefore make sure that
     * {@link #executeSyncPlayer(UUID, Runnable, Runnable)} always runs the task on the thread which owns the player.
     *
     * @return true if the server uses threaded regions, false otherwise
     */
    public default boolean isFolia() {
        return false;
    }

    /**
     * Schedule a task to run on the thread which owns the player with the given unique ID.
     * If the player is not online, then the task is scheduled to run on the global region.
     *
     * @param playerId the unique ID of the player whose thread the task should run on
     * @param task the task
     * @param retired the task which is run if the player is no longer owned by any region.
     *                May be null, in which case the {@code task} is run on the global region instead.
     *
     * @apiNote On places where this is called, the argument provided to the 'retired' parameter should actually be a
     *          value. Usually we want to:
     *          <ul>
     *          <li>load the 'offline' player data,</li>
     *          <li>apply the changes to the player's inventory,</li>
     *          <li>save the player data again.</li>
     *          </ul>
     */
    public void executeSyncPlayer(UUID playerId, Runnable task, @Nullable Runnable retired);

    /**
     * Schedule a task to run on the thread which owns the given player.
     *
     * @param player the player whose thread the task should run on
     * @param task the task
     * @param retired the task which is run if the player is no longer owned by any region. May be null.
     */
    public default void executeSyncPlayer(HumanEntity player, Runnable task, @Nullable Runnable retired) {
        executeSyncPlayer(player.getUniqueId(), task, retired);
    }

    /**
     * Schedule a task to run on the thread which owns the player with the given unique ID.
     * If the player is no longer owned by any region, then the task is run on the global region.
     *
     * @param playerId the unique ID of the player whose thread the task should run on
     * @param task the task
     */
    public default void executeSyncPlayer(UUID playerId, Runnable task) {
        executeSyncPlayer(playerId, task, null);
    }

    /**
     * Schedule a task to run on the thread which owns the given player.
     * If the player is no longer owned by any region, then the task is run on the global region.
     *
     * @param player the player whose thread the task should run on
     * @param task the task
     */
    public default void executeSyncPlayer(HumanEntity player, Runnable task) {
        executeSyncPlayer(player.getUniqueId(), task, null);
    }

    /**
     * Schedule a task to run on the global region.
     * @param task the task
     */
    public void executeSyncGlobal(Runnable task);

    /**
     * Schedule a task to run on the global region, repeatedly.
     * @param task the task
     * @param ticksInitialDelay the amount of ticks to wait before the task runs for the first time
     * @param ticksPeriod the amount of ticks between two consecutive runs of the task
     */
    public void executeSyncGlobalRepeatedly(Runnable task, long ticksInitialDelay, long ticksPeriod);

    /**
     * Schedule a task to run asynchronously.
     * @param task the task
     */
    public void executeAsync(Runnable task);

    /**
     * Schedule a task to run on the global region, after a delay.
     * @param task the task
     * @param delayTicks the delay in ticks
     */
    public void executeLaterGlobal(Runnable task, long delayTicks);

    /**
     * Schedule a task to run asynchronously, after a delay.
     * @param task the task
     * @param delayTicks the delay in ticks
     */
    public void executeLaterAsync(Runnable task, long delayTicks);

    /**
     * Try to find the scheduler which is used by the InvSee++ plugin.
     * <p>
     * This method exists because {@link com.janboerman.invsee.spigot.api.CreationOptions} does not carry a
     * {@link Scheduler} - it only carries the {@link Plugin} which created the options. If the given plugin is the
     * InvSee++ plugin itself, then its scheduler is returned; otherwise the InvSee++ plugin is looked up by name.
     *
     * @param plugin the plugin which created the {@linkplain com.janboerman.invsee.spigot.api.CreationOptions}
     * @return the scheduler used by InvSee++, or null if InvSee++ is not enabled (yet)
     */
    public static @Nullable Scheduler findScheduler(@Nullable Plugin plugin) {
        InvseeAPI api;

        if (plugin instanceof InvseePlusPlus && (api = ((InvseePlusPlus) plugin).getApi()) != null)
            return api.getScheduler();

        if (plugin != null) {
            Plugin invSeePlusPlus = plugin.getServer().getPluginManager().getPlugin("InvSeePlusPlus");
            if (invSeePlusPlus instanceof InvseePlusPlus && (api = ((InvseePlusPlus) invSeePlusPlus).getApi()) != null)
                return api.getScheduler();
        }

        return null;
    }

}
