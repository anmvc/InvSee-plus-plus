package com.janboerman.invsee.spigot.internal.inventory;

import com.janboerman.invsee.spigot.api.CreationOptions;
import com.janboerman.invsee.spigot.api.SpectatorInventory;
import com.janboerman.invsee.spigot.api.target.Target;
import com.janboerman.invsee.spigot.api.template.Mirror;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;

import java.util.UUID;

public interface StandardSpectatorInventory<Slot, NMS extends AbstractNmsInventory<Slot, Self, NMS>, Self extends StandardSpectatorInventory<Slot, NMS, Self>> extends SpectatorInventory<Slot>, Wrapper<NMS, Self> {

    public default String getSpectatedPlayerName() {
        return getInventory().targetPlayerName;
    }

    public default UUID getSpectatedPlayerId() {
        return getInventory().targetPlayerUuid;
    }

    public default String getTitle() {
        return getInventory().creationOptions.getTitle().titleFor(Target.byGameProfile(getSpectatedPlayerId(), getSpectatedPlayerName()));
    }

    public default Mirror<Slot> getMirror() {
        return getInventory().creationOptions.getMirror();
    }

    public default CreationOptions<Slot> getCreationOptions() {
        return getInventory().creationOptions.clone();
    }

    /**
     * Get the region scheduler for the target entity.
     * {\\@return} the entity scheduler of the target, or {\\@code null} if not running on Folia.
     */
    public default {\\@Nullable} EntityScheduler getTargetEntityScheduler() {
        return null;
    }

    /**
     * Get the region scheduler for the spectator entity.
     * {\\@return} the entity scheduler of the spectator, or {\\@code null} if not running on Folia.
     */
    public default {\\@Nullable} EntityScheduler getSpectatorEntityScheduler() {
        return null;
    }

    /**
     * Schedule a task that is synchronised across both the target player's EntityScheduler as well as the spectator player's EntityScheduler.
     * {\\@param} {\\@code task} the task to run
     * {\\@param} {\\@code retired} the task to run when the inventory is closed or retired
     */
    public default void executeSyncSpectator(Runnable task, Runnable retired) {
        // Default implementation for Bukkit: fallback to global scheduler
        com.janboerman.invsee.spigot.api.Scheduler scheduler = getCreationOptions().getPlugin().getApi().getScheduler();
        scheduler.executeSyncGlobal(task);
        if (retired != null) {
            scheduler.executeSyncGlobal(retired);
        }
    }

    /**
     * Optimized execution: executes directly if already on the target or spectator region thread (0 scheduler calls on Folia).
     * Otherwise falls back to executeSyncSpectator.
     */
    public default void executeDirectOrSyncSpectator(Runnable task, Runnable retired) {
        EntityScheduler target = getTargetEntityScheduler();
        EntityScheduler spectator = getSpectatorEntityScheduler();
        
        boolean onTarget = false;
        boolean onSpectator = false;
        
        // Check if we are already on the target thread (Folia)
        if (target != null) {
            // EntityScheduler.isCurrentThread() is available in Paper/Folia
            onTarget = target.isCurrentThread();
        }
        // Check if we are already on the spectator thread (Folia)
        if (spectator != null) {
            onSpectator = spectator.isCurrentThread();
        }
        
        if (onTarget || onSpectator) {
            // Execute directly (0 scheduler calls)
            task.run();
            if (retired != null) retired.run();
        } else {
            // Use standard sync (handles Folia 2-calls or Bukkit 0-calls)
            executeSyncSpectator(task, retired);
        }
    }

}
