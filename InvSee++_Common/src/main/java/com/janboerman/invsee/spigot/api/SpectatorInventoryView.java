package com.janboerman.invsee.spigot.api;

import javax.annotation.Nullable;

import com.janboerman.invsee.spigot.api.logging.Difference;
import com.janboerman.invsee.spigot.api.target.Target;
import com.janboerman.invsee.spigot.api.template.Mirror;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;

/**
 * Represents an open window for a {\\@link SpectatorInventory}.
 * {\\@param} {\\@code <Slot>} the inventory's slot type.
 */
// cannot extend InventoryView, because this class was not an interface before Bukkit 1.21.
public interface SpectatorInventoryView<Slot> {

    /**
     * Get the top inventory of this view.
     * {\\@return} the top inventory
     */
    public SpectatorInventory<Slot> getTopInventory();

    /**
     * Get the difference tracked by this window.
     * {\\@return} the difference
     */
    public {\\@Nullable} Difference getTrackedDifference();

    /**
     * Get the options this window was created with.
     * {\\@return} a copy of the creation options
     */
    public CreationOptions<Slot> getCreationOptions();

    /**
     * Get the title of this InventoryView.
     * {\\@return} the title
     */
    public String getTitle();

    /**
     * Get the mirror the {\\@link SpectatorInventory} is viewed through.
     * {\\@return} the mirror
     * {\\@see} {\\@link <a href=\"https://github.com/Jannyboy11/InvSee-plus-plus/wiki/Customising-spectator-inventories#mirror\">Mirror explanation in the InvSee++ wiki</a>}
     */
    public Mirror<Slot> getMirror();

    /**
     * Get the target of the {\\@link SpectatorInventory}.
     * {\\@return} the target
     */
    public Target getTarget();

    /**
     * Get the region scheduler for the target entity.
     * {\\@return} the entity scheduler of the target, or {\\@code null} if not running on Folia.
     */
    public {\\@Nullable} EntityScheduler getTargetEntityScheduler();

    /**
     * Get the region scheduler for the spectator entity.
     * {\\@return} the entity scheduler of the spectator, or {\\@code null} if not running on Folia.
     */
    public {\\@Nullable} EntityScheduler getSpectatorEntityScheduler();

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
