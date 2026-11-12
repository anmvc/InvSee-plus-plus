package com.janboerman.invsee.spigot.impl_1_18_2_R2;

import com.janboerman.invsee.spigot.api.EnderSpectatorInventory;
import com.janboerman.invsee.spigot.api.EnderSpectatorInventoryView;
import com.janboerman.invsee.spigot.api.logging.Difference;
import com.janboerman.invsee.spigot.api.logging.DifferenceTracker;
import com.janboerman.invsee.spigot.api.template.EnderChestSlot;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;

import org.bukkit.craftbukkit.v1_18_R2.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import javax.annotation.Nullable;

class EnderBukkitInventoryView extends BukkitInventoryView<EnderChestSlot> implements EnderSpectatorInventoryView {

    final EnderNmsContainer nms;

    EnderBukkitInventoryView(EnderNmsContainer nms) {
        super(nms.creationOptions);
        this.nms = nms;
    }

    @Override
    public EnderSpectatorInventory getTopInventory() {
        return nms.top.bukkit();
    }

    @Override
    public PlayerInventory getBottomInventory() {
        return nms.player.getBukkitEntity().getInventory();
    }

    @Override
    public HumanEntity getPlayer() {
        return nms.player.getBukkitEntity();
    }

    @Override
    public void setItem(int slot, ItemStack item) {
// FOLIASYNC: schedule task ONLY if we are on Folia (target/spectator schedulers exist)
        // Otherwise, execute directly on the calling thread (Bukkit/Paper fallback) to minimize scheduler calls.
        EntityScheduler targetScheduler = nms.getTargetEntityScheduler();
        EntityScheduler spectatorScheduler = nms.getSpectatorEntityScheduler();
        
        if (targetScheduler != null && spectatorScheduler != null) {
            Runnable task = () -> {
                if (slot >= 0) {
                    nms.getSlot(slot).set(stack);
                } else {
                    nms.player.drop(stack, false);
                }
            };
            // Execute sync spectator task (blocks until done in Folia)
            executeSyncSpectator(task, null);
        } else {
            // Bukkit/Paper: execute directly (0 scheduler calls)
            if (slot >= 0) {
                nms.getSlot(slot).set(stack);
            } else {
                nms.player.drop(stack, false);
            }
        }
    }

    @Override
    public ItemStack getItem(int slot) {
// FOLIASYNC: schedule task ONLY if we are on Folia (target/spectator schedulers exist)
        // Otherwise, execute directly on the calling thread (Bukkit/Paper fallback) to minimize scheduler calls.
        EntityScheduler targetScheduler = nms.getTargetEntityScheduler();
        EntityScheduler spectatorScheduler = nms.getSpectatorEntityScheduler();
        
        if (targetScheduler != null && spectatorScheduler != null) {
            final ItemStack[] resultStack = { null };
            Runnable task = () -> {
                net.minecraft.world.item.ItemStack nmsStack;
                if (slot < 0) {
                    resultStack[0] = null;
                } else {
                    if (slot < nms.top.getContainerSize()) {
                        nmsStack = nms.top.getContents().get(slot);
                    } else {
                        nmsStack = nms.getSlot(slot).getItem();
                    }
                    resultStack[0] = CraftItemStack.asCraftMirror(nmsStack);
                }
            };
            // Execute sync spectator task (blocks until done in Folia)
            executeSyncSpectator(task, null);
            return resultStack[0];
        } else {
            // Bukkit/Paper: execute directly (0 scheduler calls)
            net.minecraft.world.item.ItemStack nmsStack;
            if (slot < 0) {
                return null;
            } else {
                if (slot < nms.top.getContainerSize()) {
                    nmsStack = nms.top.getContents().get(slot);
                } else {
                    nmsStack = nms.getSlot(slot).getItem();
                }
                return CraftItemStack.asCraftMirror(nmsStack);
            }
        }
    }

    @Override
    public @Nullable Difference getTrackedDifference() {
        DifferenceTracker tracker = nms.tracker;
        return tracker == null ? null : tracker.getDifference();
    }
    
    @Override
    public InventoryType getType() {
    	return InventoryType.CHEST;
    }

    /**
     * Get the region scheduler for the target entity.
     * {\@return} the entity scheduler of the target, or {\@code null} if not running on Folia.
     */
    @Override
    public {\@Nullable} EntityScheduler getTargetEntityScheduler() {
        // In 1.18.2, Folia support is not natively implemented in the platform, but we can return null
        // or try to detect Folia if the platform is upgraded.
        // For now, we return null.
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
    public void executeSyncSpectator(Runnable task, Runnable retired) {
        // Fallback to global scheduler for Bukkit
        com.janboerman.invsee.spigot.api.Scheduler scheduler = nms.creationOptions.getPlugin().getApi().getScheduler();
        scheduler.executeSyncGlobal(task);
        if (retired != null) {
            scheduler.executeSyncGlobal(retired);
        }
    }
}
