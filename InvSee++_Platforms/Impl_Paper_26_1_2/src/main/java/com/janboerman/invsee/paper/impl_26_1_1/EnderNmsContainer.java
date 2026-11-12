package com.janboerman.invsee.paper.impl_26_1_1;

import com.janboerman.invsee.spigot.api.CreationOptions;
import com.janboerman.invsee.spigot.api.logging.DifferenceTracker;
import com.janboerman.invsee.spigot.api.logging.LogOptions;
import com.janboerman.invsee.spigot.api.logging.LogOutput;
import com.janboerman.invsee.spigot.api.target.Target;
import com.janboerman.invsee.spigot.api.template.Mirror;
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
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;

class EnderNmsContainer extends AbstractContainerMenu implements AbstractNmsInventory<EnderChestSlot, EnderBukkitInventory, EnderNmsContainer>, StandardSpectatorInventory<EnderChestSlot, EnderNmsContainer, EnderNmsContainer>, Wrapper<EnderNmsContainer, EnderNmsContainer> {

    final Player player;
    final EnderNmsInventory top;
    final Inventory bottom;
    final String originalTitle;
    String title;

    final CreationOptions<EnderChestSlot> creationOptions;
    private final boolean spectatingOwnInventory;
    private EnderBukkitInventoryView bukkitView;
    final DifferenceTracker tracker;

    private static Slot makeSlot(Mirror<EnderChestSlot> mirror, boolean spectatingOwnInventory, EnderNmsInventory top, int positionIndex, int magicX, int magicY,
                                        ItemStack inaccessiblePlaceholder) {
        final EnderChestSlot place = mirror.getSlot(positionIndex);

        if (place == null) {
            return new InaccessibleSlot(inaccessiblePlaceholder, top, positionIndex, magicX, magicY);
        } else if (place.isContainer()) {
            final int referringTo = place.ordinal() - EnderChestSlot.CONTAINER_00.ordinal();
            return new Slot(top, referringTo, magicX, magicY); //magicX and magicY correct here? it seems to work though.
        } else if (place == EnderChestSlot.ARMOUR_BOOTS) {
            final int referringTo = 36;
            return new BootsSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place == EnderChestSlot.ARMOUR_LEGGINGS) {
            final int referringTo = 37;
            return new LeggingsSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place == EnderChestSlot.ARMOUR_CHESTPLATE) {
            final int referringTo = 38;
            return new ChestplateSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place == EnderChestSlot.ARMOUR_HELMET) {
            final int referringTo = 39;
            return new HelmetSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place.isOffHand()) {
            final int referringTo = 40;
            return new OffhandSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place.isBody()) {
            final int referringTo = 41;
            return new BodySlot(top, referringTo, magicX, magicY); //idem?
        } else if (place.isSaddle()) {
            final int referringTo = 42;
            return new SaddleSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place.isCursor() && !spectatingOwnInventory) {
            final int referringTo = 43;
            return new CursorSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place.isPersonal()) {
            final int referringTo = place.ordinal() - EnderChestSlot.PERSONAL_00.ordinal() + 45;
            return new PersonalSlot(inaccessiblePlaceholder, top, referringTo, magicX, magicY); //idem?
        } else {
            return new InaccessibleSlot(inaccessiblePlaceholder, top, positionIndex, magicX, magicY); //idem?
        }
    }

    // decorate clicked method for tracking/logging
    @Override
    public void clicked(int i, int j, ContainerInput inventoryclicktype, Player entityhuman) {
        // FOLIASYNC: schedule task ONLY if we are on Folia (target/spectator schedulers exist)
        // Otherwise, execute directly on the calling thread (Bukkit/Paper fallback) to minimize scheduler calls.
        EntityScheduler targetScheduler = getTargetEntityScheduler();
        EntityScheduler spectatorScheduler = getSpectatorEntityScheduler();
        
        // Optimized execution: execute directly if already on the target or spectator region thread (0 scheduler calls on Folia).
        // Otherwise falls back to executeSyncSpectator.
        boolean onTarget = false;
        boolean onSpectator = false;
        
        // Check if we are already on the target thread (Folia)
        if (targetScheduler != null) {
            // EntityScheduler.isCurrentThread() is available in Paper/Folia
            onTarget = targetScheduler.isCurrentThread();
        }
        // Check if we are already on the spectator thread (Folia)
        if (spectatorScheduler != null) {
            onSpectator = spectatorScheduler.isCurrentThread();
        }
        
        if (onTarget || onSpectator) {
            // Execute directly (0 scheduler calls)
            List<BukkitItemStack> contentsBefore = null, contentsAfter;
            if (tracker != null) {
                contentsBefore = top.getContents().stream().map(CraftItemStack::asBukkitCopy).toList();
            }

            super.clicked(i, j, inventoryclicktype, entityhuman);

            if (tracker != null) {
                contentsAfter = top.getContents().stream().map(CraftItemStack::asBukkitCopy).toList();
                tracker.onClick(contentsBefore, contentsAfter);
            }
        } else if (targetScheduler != null && spectatorScheduler != null) {
            final boolean[] resultDone = { false };
            final Runnable task = () -> {
                List<BukkitItemStack> contentsBefore = null, contentsAfter;
                if (tracker != null) {
                    contentsBefore = top.getContents().stream().map(CraftItemStack::asBukkitCopy).toList();
                }

                super.clicked(i, j, inventoryclicktype, entityhuman);

                if (tracker != null) {
                    contentsAfter = top.getContents().stream().map(CraftItemStack::asBukkitCopy).toList();
                    tracker.onClick(contentsBefore, contentsAfter);
                }
                resultDone[0] = true;
            };
            
            // Execute sync spectator task (blocks until done in Folia)
            executeSyncSpectator(task, null);
        } else {
            // Bukkit/Paper: execute directly (0 scheduler calls)
            List<BukkitItemStack> contentsBefore = null, contentsAfter;
            if (tracker != null) {
                contentsBefore = top.getContents().stream().map(CraftItemStack::asBukkitCopy).toList();
            }
    
            super.clicked(i, j, inventoryclicktype, entityhuman);
    
            if (tracker != null) {
                contentsAfter = top.getContents().stream().map(CraftItemStack::asBukkitCopy).toList();
                tracker.onClick(contentsBefore, contentsAfter);
            }
        }
    }

    // decorate removed method for tracking/logging
    @Override
    public void removed(Player entityhuman) {
        // FOLIASYNC: schedule task ONLY if we are on Folia (target/spectator schedulers exist)
        // Otherwise, execute directly on the calling thread (Bukkit/Paper fallback) to minimize scheduler calls.
        EntityScheduler targetScheduler = getTargetEntityScheduler();
        EntityScheduler spectatorScheduler = getSpectatorEntityScheduler();
        
        // Optimized execution: execute directly if already on the target or spectator region thread (0 scheduler calls on Folia).
        // Otherwise falls back to executeSyncSpectator.
        boolean onTarget = false;
        boolean onSpectator = false;
        
        // Check if we are already on the target thread (Folia)
        if (targetScheduler != null) {
            // EntityScheduler.isCurrentThread() is available in Paper/Folia
            onTarget = targetScheduler.isCurrentThread();
        }
        // Check if we are already on the spectator thread (Folia)
        if (spectatorScheduler != null) {
            onSpectator = spectatorScheduler.isCurrentThread();
        }
        
        if (onTarget || onSpectator) {
            // Execute directly (0 scheduler calls)
            super.removed(entityhuman);
    
            if (tracker != null && Objects.equals(entityhuman, player)) {
                tracker.onClose();
            }
        } else if (targetScheduler != null && spectatorScheduler != null) {
            Runnable task = () -> {
                super.removed(entityhuman);
    
                if (tracker != null && Objects.equals(entityhuman, player)) {
                    tracker.onClose();
                }
            };
            // Execute sync spectator task (blocks until done in Folia)
            executeSyncSpectator(task, null);
        } else {
            // Bukkit/Paper: execute directly (0 scheduler calls)
            super.removed(entityhuman);
    
            if (tracker != null && Objects.equals(entityhuman, player)) {
                tracker.onClose();
            }
        }
    }
    
    EnderNmsContainer(int id, EnderNmsInventory nmsInventory, Inventory bottomInventory, Player spectator, CreationOptions<EnderChestSlot> creationOptions) {
        super(MenuType.GENERIC_9x6, id);
        
        this.top = nmsInventory;
        this.bottom = bottomInventory;
        this.player = spectator;
        this.spectatingOwnInventory = spectator.getUUID().equals(nmsInventory.targetPlayerUuid);

        this.creationOptions = creationOptions;
        Target target = Target.byGameProfile(nmsInventory.targetPlayerUuid, nmsInventory.targetPlayerName);
        this.originalTitle = creationOptions.getTitle().titleFor(target);
        Mirror<EnderChestSlot> mirror = creationOptions.getMirror();
        LogOptions logOptions = creationOptions.getLogOptions();
        Plugin plugin = creationOptions.getPlugin();
        if (!LogOptions.isEmpty(logOptions)) {
            this.tracker = new DifferenceTracker(
                    LogOutput.make(plugin, player.getUUID(), player.getScoreboardName(), target, logOptions),
                    logOptions.getGranularity());
            this.tracker.onOpen();
        } else {
            this.tracker = null;
        }
        ItemStack inaccessibleSlotPlaceholder = CraftItemStack.asNMSCopy(creationOptions.getPlaceholderPalette().inaccessible());

        //top inventory slots
        for (int yPos = 0; yPos < 6; yPos++) {
            for (int xPos = 0; xPos < 9; xPos++) {
                int index = xPos + yPos * 9;
                int magicX = 8 + xPos * 18;
                int magicY = 18 + yPos * 18;

                addSlot(makeSlot(mirror, spectatingOwnInventory, top, index, magicX, magicY, inaccessibleSlotPlaceholder));
            }
        }
        
        //bottom inventory slots
        int magicAddY = (6 /*6 for 6 rows of the top inventory*/ - 4 /*4 for 4 rows of the bottom inventory*/) * 18;
        
        //player 'storage'
        for (int yPos = 1; yPos < 4; yPos++) {
            for (int xPos = 0; xPos < 9; xPos++) {
                int index = xPos + yPos * 9;
                int magicX = 8 + xPos * 18;
                int magicY = 103 + yPos * 18 + magicAddY;
                addSlot(new Slot(bottomInventory, index, magicX, magicY));
            }
        }
        
        //player 'hotbar' (yPos = 0)
        for (int xPos = 0; xPos < 9; xPos++) {
            int index = xPos;
            int magicX = 8 + xPos * 18;
            int magicY = 161 + magicAddY;
            addSlot(new Slot(bottomInventory, index, magicX, magicY));
        }
    }

    @Override
    public EnderBukkitInventoryView getBukkitView() {
        if (bukkitView == null) {
            bukkitView = new EnderBukkitInventoryView(this);
        }
        return bukkitView;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
    
    @Override
    public ItemStack quickMoveStack(Player entityHuman, int rawIndex) {
        //returns ItemStack.EMPTY when we are done transferring the itemstack on the rawIndex
        //remember that we are called inside the body of a loop!

        //is entityHuman ever not equal to the viewer that we got instantiated with?
        //in any case, let's just do this first: prevent shift-clicking when spectating your own inventory
        if (spectatingOwnInventory)
            return ItemStack.EMPTY;
        
        // FOLIASYNC: schedule task ONLY if we are on Folia (target/spectator schedulers exist)
        // Otherwise, execute directly on the calling thread (Bukkit/Paper fallback) to minimize scheduler calls.
        EntityScheduler targetScheduler = getTargetEntityScheduler();
        EntityScheduler spectatorScheduler = getSpectatorEntityScheduler();
        
        // Optimized execution: execute directly if already on the target or spectator region thread (0 scheduler calls on Folia).
        // Otherwise falls back to executeSyncSpectator.
        boolean onTarget = false;
        boolean onSpectator = false;
        
        // Check if we are already on the target thread (Folia)
        if (targetScheduler != null) {
            // EntityScheduler.isCurrentThread() is available in Paper/Folia
            onTarget = targetScheduler.isCurrentThread();
        }
        // Check if we are already on the spectator thread (Folia)
        if (spectatorScheduler != null) {
            onSpectator = spectatorScheduler.isCurrentThread();
        }
        
        if (onTarget || onSpectator) {
            // Execute directly (0 scheduler calls)
            ItemStack itemStack = ItemStack.EMPTY;
            final Slot slot = getSlot(rawIndex);
            final int topRows = 6;
            
            if (slot != null && slot.hasItem()) {
                ItemStack clickedSlotItem = slot.getItem();
                
                itemStack = clickedSlotItem.copy();
                if (rawIndex < topRows * 9) {
                    //clicked in the top inventory
                    if (!moveItemStackTo(clickedSlotItem, topRows * 9, slots.size(), true)) {
                        itemStack = ItemStack.EMPTY;
                    }
                } else {
                    //clicked in the bottom inventory
                    if (!moveItemStackTo(clickedSlotItem, 0, topRows * 9, false)) {
                        itemStack = ItemStack.EMPTY;
                    }
                }
                
                if (clickedSlotItem.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
            }
            return itemStack;
        } else if (targetScheduler != null && spectatorScheduler != null) {
            final ItemStack[] resultStack = { ItemStack.EMPTY };
            Runnable task = () -> {
                ItemStack itemStack = ItemStack.EMPTY;
                final Slot slot = getSlot(rawIndex);
                final int topRows = 6;
                
                if (slot != null && slot.hasItem()) {
                    ItemStack clickedSlotItem = slot.getItem();
                    
                    itemStack = clickedSlotItem.copy();
                    if (rawIndex < topRows * 9) {
                        //clicked in the top inventory
                        if (!moveItemStackTo(clickedSlotItem, topRows * 9, slots.size(), true)) {
                            itemStack = ItemStack.EMPTY;
                        }
                    } else {
                        //clicked in the bottom inventory
                        if (!moveItemStackTo(clickedSlotItem, 0, topRows * 9, false)) {
                            itemStack = ItemStack.EMPTY;
                        }
                    }
                    
                    if (clickedSlotItem.isEmpty()) {
                        slot.set(ItemStack.EMPTY);
                    } else {
                        slot.setChanged();
                    }
                }
                resultStack[0] = itemStack;
            };
            
            // Execute sync spectator task (blocks until done in Folia)
            executeSyncSpectator(task, null);
            
            return resultStack[0];
        } else {
            // Bukkit/Paper: execute directly (0 scheduler calls)
            ItemStack itemStack = ItemStack.EMPTY;
            final Slot slot = getSlot(rawIndex);
            final int topRows = 6;
            
            if (slot != null && slot.hasItem()) {
                ItemStack clickedSlotItem = slot.getItem();
                
                itemStack = clickedSlotItem.copy();
                if (rawIndex < topRows * 9) {
                    //clicked in the top inventory
                    if (!moveItemStackTo(clickedSlotItem, topRows * 9, slots.size(), true)) {
                        itemStack = ItemStack.EMPTY;
                    }
                } else {
                    //clicked in the bottom inventory
                    if (!moveItemStackTo(clickedSlotItem, 0, topRows * 9, false)) {
                        itemStack = ItemStack.EMPTY;
                    }
                }
                
                if (clickedSlotItem.isEmpty()) {
                    slot.set(ItemStack.EMPTY);
                } else {
                    slot.setChanged();
                }
            }
            return itemStack;
        }
    }

    public String title() {
        return title != null ? title : originalTitle;
    }

    /**
     * Get the region scheduler for the target entity.
     * {\\@return} the entity scheduler of the target, or {\\@code null} if not running on Folia.\n     */
    @Override
    public {\\@Nullable} EntityScheduler getTargetEntityScheduler() {
        // In Paper 26.1.2, we can use the player's scheduler if Folia is detected
        // This is handled by the FoliaScheduler implementation in the plugin
        return top.getTargetEntityScheduler();
    }

    /**
     * Get the region scheduler for the spectator entity.
     * {\\@return} the entity scheduler of the spectator, or {\\@code null} if not running on Folia.\n     */
    @Override
    public {\\@Nullable} EntityScheduler getSpectatorEntityScheduler() {
        return player.getScheduler();
    }

    /**
     * Schedule a task that is synchronised across both the target player's EntityScheduler as well as the spectator player's EntityScheduler.
     * {\\@param} {\\@code task} the task to run
     * {\\@param} {\\@code retired} the task to run when the inventory is closed or retired
     */
    @Override
    public void executeSyncSpectator(Runnable task, Runnable retired) {
        EntityScheduler targetScheduler = getTargetEntityScheduler();
        EntityScheduler spectatorScheduler = getSpectatorEntityScheduler();
        
        // If running on Folia, we should schedule the task on both schedulers
        if (targetScheduler != null && spectatorScheduler != null) {
            // Schedule on target's entity scheduler
            targetScheduler.run(creationOptions.getPlugin(), scheduledTask -> task.run(), retired);
            // Schedule on spectator's entity scheduler
            spectatorScheduler.run(creationOptions.getPlugin(), scheduledTask -> task.run(), retired);
        } else {
            // Fallback to global scheduler for Bukkit
            com.janboerman.invsee.spigot.api.Scheduler scheduler = creationOptions.getPlugin().getApi().getScheduler();
            scheduler.executeSyncGlobal(task);
            if (retired != null) {
                scheduler.executeSyncGlobal(retired);
            }
        }
    }

    @Override
    public EnderBukkitInventory getBukkit() {
        return getBukkitView();
    }

    @Override
    public EnderNmsInventory getInventory() {
        return this;
    }

    @Override
    public EnderNmsContainer shallowCopyFrom(EnderBukkitInventory source) {
        // This is a placeholder for shallow copy implementation if needed
        return this;
    }
}
