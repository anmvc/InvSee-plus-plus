package com.janboerman.invsee.spigot.impl_1_12_R1;

import java.util.Objects;

import com.janboerman.invsee.spigot.api.CreationOptions;
import com.janboerman.invsee.spigot.api.SpectatorInventory;
import com.janboerman.invsee.spigot.api.SpectatorInventoryView;
import com.janboerman.invsee.spigot.api.target.Target;
import com.janboerman.invsee.spigot.api.template.Mirror;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;

import org.bukkit.inventory.InventoryView;

abstract class BukkitInventoryView<Slot> extends InventoryView implements SpectatorInventoryView<Slot> {

    private final CreationOptions<Slot> creationOptions;
    protected Target target;

    BukkitInventoryView(CreationOptions<Slot> creationOptions) {
        this.creationOptions = Objects.requireNonNull(creationOptions, "creation options cannot be null");
    }

    @Override
    public CreationOptions<Slot> getCreationOptions() {
        return creationOptions.clone();
    }

    @Override
    public Mirror<Slot> getMirror() {
        return creationOptions.getMirror();
    }

    @Override
    public Target getTarget() {
        SpectatorInventory<Slot> top = getTopInventory();
        return target == null ? target = Target.byGameProfile(top.getSpectatedPlayerId(), top.getSpectatedPlayerName()) : target;
    }

    /**
     * Get the region scheduler for the target entity.
     * {\@return} the entity scheduler of the target, or {\@code null} if not running on Folia.
     */
    @Override
    public {\@Nullable} EntityScheduler getTargetEntityScheduler() {
        return null;
    }

    /**
     * Get the region scheduler for the spectator entity.
     * {\@return} the entity scheduler of the spectator, or {\@code null} if not running on Folia.
     */
    @Override
    public {\@Nullable} EntityScheduler getSpectatorEntityScheduler() {
        return null;
    }

    /**
     * Schedule a task that is synchronised across both the target player's EntityScheduler as well as the spectator player's EntityScheduler.
     * {\@param} {\@code task} the task to run
     * {\@param} {\@code retired} the task to run when the inventory is closed or retired
     */
    @Override
    public default void executeSyncSpectator(Runnable task, Runnable retired) {
        // Default implementation for Bukkit: fallback to global scheduler
        com.janboerman.invsee.spigot.api.Scheduler scheduler = creationOptions.getPlugin().getApi().getScheduler();
        scheduler.executeSyncGlobal(task);
        if (retired != null) {
            scheduler.executeSyncGlobal(retired);
        }
    }

}
