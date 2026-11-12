package com.janboerman.invsee.spigot.impl_1_18_2_R2;

import com.janboerman.invsee.spigot.api.MainSpectatorInventory;
import com.janboerman.invsee.spigot.api.MainSpectatorInventoryView;
import com.janboerman.invsee.spigot.api.logging.Difference;
import com.janboerman.invsee.spigot.api.logging.DifferenceTracker;
import com.janboerman.invsee.spigot.api.template.PlayerInventorySlot;

import org.bukkit.craftbukkit.v1_18_R2.inventory.CraftItemStack;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import javax.annotation.Nullable;

class MainBukkitInventoryView extends BukkitInventoryView<PlayerInventorySlot> implements MainSpectatorInventoryView {

    final MainNmsContainer nms;

    MainBukkitInventoryView(MainNmsContainer nms) {
        super(nms.creationOptions);
        this.nms = nms;
    }

    @Override
    public MainSpectatorInventory getTopInventory() {
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
    public String getTitle() {
        return nms.title;
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
}
