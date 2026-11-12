package com.janboerman.invsee.paper.impl_26_1_1;

import com.google.common.base.Preconditions;
import com.janboerman.invsee.spigot.api.CreationOptions;
import com.janboerman.invsee.spigot.api.EnderSpectatorInventory;
import com.janboerman.invsee.spigot.api.EnderSpectatorInventoryView;
import com.janboerman.invsee.spigot.api.logging.Difference;
import com.janboerman.invsee.spigot.api.logging.DifferenceTracker;
import com.janboerman.invsee.spigot.api.template.EnderChestSlot;
import com.janboerman.invsee.spigot.internal.inventory.AbstractNmsInventory;
import com.janboerman.invsee.spigot.internal.inventory.ShallowCopy;
import com.janboerman.invsee.spigot.internal.inventory.StandardSpectatorInventory;
import com.janboerman.invsee.spigot.internal.inventory.Wrapper;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.bukkit.craftbukkit.inventory.CraftInventoryView;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.inventory.CraftMenuType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack as BukkitItemStack;
import org.bukkit.inventory.MenuType as BukkitMenuType;
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
    public InventoryType getType() {
        return InventoryType.CHEST;
    }

    @Override
    public String getTitle() {
        return nms.title();
    }

    @Override
    public String getOriginalTitle() {
        return nms.originalTitle;
    }

    @Override
    public void setTitle(String title) {
        CraftInventoryView.sendInventoryTitleChange(this, title);
        nms.title = title;
    }

    @Override
    public BukkitMenuType getMenuType() {
        return CraftMenuType.minecraftToBukkit(nms.getType());
    }

    @Override
    public void setItem(int slot, BukkitItemStack item) {
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
    public BukkitItemStack getItem(int slot) {
// FOLIASYNC: schedule task ONLY if we are on Folia (target/spectator schedulers exist)
        // Otherwise, execute directly on the calling thread (Bukkit/Paper fallback) to minimize scheduler calls.
        EntityScheduler targetScheduler = nms.getTargetEntityScheduler();
        EntityScheduler spectatorScheduler = nms.getSpectatorEntityScheduler();
        
        if (targetScheduler != null && spectatorScheduler != null) {
            final BukkitItemStack[] resultStack = { null };
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
    public void setCursor(BukkitItemStack itemStack) {
        getPlayer().setItemOnCursor(itemStack);
    }

    @Override
    public BukkitItemStack getCursor() {
        return getPlayer().getItemOnCursor();
    }

    @Override
    public Inventory getInventory(int rawSlot) {
        if (rawSlot == OUTSIDE || rawSlot == -1) {
            return null;
        } else {
            Preconditions.checkArgument(rawSlot >= 0, "Negative, non outside slot %s", rawSlot);
            Preconditions.checkArgument(rawSlot < this.countSlots(), "Slot %s greater than inventory slot count", rawSlot);
            return rawSlot < this.getTopInventory().getSize() ? this.getTopInventory() : this.getBottomInventory();
        }
    }

    @Override
    public int convertSlot(int rawSlot) {
        int topSize = getTopInventory().getSize();
        if (rawSlot < topSize) {
            return rawSlot;
        } else {
            int slot = rawSlot - topSize;
            if (slot >= 27) {
                slot -= 27;
            } else {
                slot += 9;
            }
            return slot;
        }
    }

    @Override
    public InventoryType.SlotType getSlotType(int slot) {
        if (slot < 0) {
            return InventoryType.SlotType.OUTSIDE;
        } else {
            int slotCount = countSlots();
            if (slotCount - 9 <= slot && slot < slotCount) {
                return InventoryType.SlotType.QUICKBAR;
            } else {
                return InventoryType.SlotType.CONTAINER;
            }
        }
    }

    @Override
    public void open() {
        getPlayer().openInventory(this);
    }

    @Override
    public void close() {
        getPlayer().closeInventory();
    }

    @Override
    public int countSlots() {
        return getTopInventory().getSize() + getBottomInventory().getStorageContents().length;
    }

    @Override
    public boolean setProperty(Property property, int value) {
        return getPlayer().setWindowProperty(property, value);
    }

    @Override
    public @Nullable Difference getTrackedDifference() {
        DifferenceTracker tracker = nms.tracker;
        return tracker == null ? null : tracker.getDifference();
    }

    /**
     * Get the region scheduler for the target entity.
     * {\@return} the entity scheduler of the target, or {\@code null} if not running on Folia.
     */
    @Override
    public {\@Nullable} EntityScheduler getTargetEntityScheduler() {
        return nms.getTargetEntityScheduler();
    }

    /**
     * Get the region scheduler for the spectator entity.
     * {\@return} the entity scheduler of the spectator, or {\@code null} if not running on Folia.
     */
    @Override
    public {\@Nullable} EntityScheduler getSpectatorEntityScheduler() {
        return nms.getSpectatorEntityScheduler();
    }

    /**
     * Schedule a task that is synchronised across both the target player's EntityScheduler as well as the spectator player's EntityScheduler.
     * {\@param} {\@code task} the task to run
     * {\@param} {\@code retired} the task to run when the inventory is closed or retired
     */
    @Override
    public void executeSyncSpectator(Runnable task, Runnable retired) {
        EntityScheduler targetScheduler = getTargetEntityScheduler();
        EntityScheduler spectatorScheduler = getSpectatorEntityScheduler();
        
        // If running on Folia, we should schedule the task on both schedulers
        if (targetScheduler != null && spectatorScheduler != null) {
            // Schedule on target's entity scheduler
            targetScheduler.run(nms.creationOptions.getPlugin(), scheduledTask -> task.run(), retired);
            // Schedule on spectator's entity scheduler
            spectatorScheduler.run(nms.creationOptions.getPlugin(), scheduledTask -> task.run(), retired);
        } else {
            // Fallback to global scheduler for Bukkit
            com.janboerman.invsee.spigot.api.Scheduler scheduler = nms.creationOptions.getPlugin().getApi().getScheduler();
            scheduler.executeSyncGlobal(task);
            if (retired != null) {
                scheduler.executeSyncGlobal(retired);
            }
        }
    }
}
