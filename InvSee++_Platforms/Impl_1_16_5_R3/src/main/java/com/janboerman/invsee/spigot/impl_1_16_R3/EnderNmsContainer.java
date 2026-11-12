package com.janboerman.invsee.spigot.impl_1_16_R3;

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
import net.minecraft.server.v1_16_R3.*;

import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

class EnderNmsContainer extends Container {

    final EntityHuman player;
    final EnderNmsInventory top;
    final PlayerInventory bottom;
    final String title;
    final CreationOptions<EnderChestSlot> creationOptions;

    private final boolean spectatingOwnInventory;
    private EnderBukkitInventoryView bukkitView;
    final DifferenceTracker tracker;

    private static Slot makeSlot(Mirror<EnderChestSlot> mirror, boolean spectatingOwnInventory, EnderNmsInventory top, int positionIndex, int magicX, int magicY, ItemStack inaccessiblePlaceholder) {
        final EnderChestSlot place = mirror.getSlot(positionIndex);

        if (place == null) {
            return new InaccessiblePlaceholderSlot(inaccessiblePlaceholder, top, positionIndex, magicX, magicY);
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
        } else if (place.isPersonal()) {
            final int referringTo = place.ordinal() - EnderChestSlot.PERSONAL_00.ordinal() + 45;
            return new PersonalSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place.isOffHand()) {
            final int referringTo = 40;
            return new OffhandSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place.isCursor() && !spectatingOwnInventory) {
            final int referringTo = 41;
            return new CursorSlot(top, referringTo, magicX, magicY); //idem?
        } else {
            return new InaccessiblePlaceholderSlot(inaccessiblePlaceholder, top, positionIndex, magicX, magicY); //idem?
        }
    }

    //clicked
    @Override
    public ItemStack a(int i, int j, InventoryClickType inventoryclicktype, EntityHuman entityhuman) {
        List<org.bukkit.inventory.ItemStack> contentsBefore = null, contentsAfter;
        if (tracker != null) {
            contentsBefore = top.getContents().stream().map(CraftItemStack::asBukkitCopy).collect(Collectors.toList());
        }

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
        
        ItemStack result;
        if (onTarget || onSpectator) {
            // Execute directly (0 scheduler calls)
            result = super.a(i, j, inventoryclicktype, entityhuman);
            
            if (tracker != null) {
                List<org.bukkit.inventory.ItemStack> contentsAfter = top.getContents().stream().map(CraftItemStack::asBukkitCopy).collect(Collectors.toList());
                tracker.onClick(contentsBefore, contentsAfter);
            }
        } else if (targetScheduler != null && spectatorScheduler != null) {
            // Folia: schedule task that is synchronised across both the target player's EntityScheduler as well as the spectator player's EntityScheduler
            final boolean[] resultDone = { false };
            final ItemStack[] resultStack = { null };
            final Runnable task = () -> {
                resultStack[0] = super.a(i, j, inventoryclicktype, entityhuman);
                
                if (tracker != null) {
                    List<org.bukkit.inventory.ItemStack> contentsAfter = top.getContents().stream().map(CraftItemStack::asBukkitCopy).collect(Collectors.toList());
                    tracker.onClick(contentsBefore, contentsAfter);
                }
                resultDone[0] = true;
            };
            
            // Execute sync spectator task (blocks until done in Folia)
            executeSyncSpectator(task, null);
            result = resultStack[0] != null ? resultStack[0] : ItemStack.a;
        } else {
            // Bukkit/Paper: execute directly (0 scheduler calls)
            result = super.a(i, j, inventoryclicktype, entityhuman);
            
            if (tracker != null) {
                List<org.bukkit.inventory.ItemStack> contentsAfter = top.getContents().stream().map(CraftItemStack::asBukkitCopy).collect(Collectors.toList());
                tracker.onClick(contentsBefore, contentsAfter);
            }
        }
        
        return result;
    }

    //removed
    @Override
    public void b(EntityHuman entityhuman) {
        // FOLIASYNC: schedule task ONLY if we are on Folia
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
            super.b(entityhuman);
            if (tracker != null && Objects.equals(entityhuman, player)) {
                tracker.onClose();
            }
        } else if (targetScheduler != null && spectatorScheduler != null) {
            Runnable task = () -> {
                super.b(entityhuman);
                if (tracker != null && Objects.equals(entityhuman, player)) {
                    tracker.onClose();
                }
            };
            executeSyncSpectator(task, null);
        } else {
            // Bukkit/Paper: execute directly (0 scheduler calls)
            super.b(entityhuman);
            if (tracker != null && Objects.equals(entityhuman, player)) {
                tracker.onClose();
            }
        }
    }

    //shiftClick
    @Override
    public ItemStack shiftClick(EntityHuman entityhuman, int rawIndex) {
        //returns EMPTY_STACK when we are done transferring the itemstack on the rawIndex
        //remember that we are called inside the body of a loop!

        if (spectatingOwnInventory)
            return InvseeImpl.EMPTY_STACK;

        List<Slot> slots = super.slots;

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
            ItemStack itemstack = InvseeImpl.EMPTY_STACK;
            Slot slot = slots.get(rawIndex);
            final int topRows = 6;

            if (slot != null && slot.hasItem()) {
                ItemStack clickedSlotItem = slot.getItem();

                itemstack = clickedSlotItem.cloneItemStack();
                if (rawIndex < topRows * 9) {
                    //clicked in the top inventory
                    if (!doShiftClickTransfer(clickedSlotItem, topRows * 9, slots.size(), true)) {
                        return InvseeImpl.EMPTY_STACK;
                    }
                } else {
                    //clicked in the bottom inventory
                    if (!doShiftClickTransfer(clickedSlotItem, 0, topRows * 9, false)) {
                        return InvseeImpl.EMPTY_STACK;
                    }
                }

                if (clickedSlotItem.isEmpty()) {
                    slot.set(InvseeImpl.EMPTY_STACK);
                } else {
                    slot.d();
                }
            }
            return itemstack;
        } else if (targetScheduler != null && spectatorScheduler != null) {
            final ItemStack[] resultStack = { InvseeImpl.EMPTY_STACK };
            final Runnable task = () -> {
                ItemStack itemstack = InvseeImpl.EMPTY_STACK;
                Slot slot = slots.get(rawIndex);
                final int topRows = 6;

                if (slot != null && slot.hasItem()) {
                    ItemStack clickedSlotItem = slot.getItem();

                    itemstack = clickedSlotItem.cloneItemStack();
                    if (rawIndex < topRows * 9) {
                        //clicked in the top inventory
                        if (!doShiftClickTransfer(clickedSlotItem, topRows * 9, slots.size(), true)) {
                            resultStack[0] = InvseeImpl.EMPTY_STACK;
                            return;
                        }
                    } else {
                        //clicked in the bottom inventory
                        if (!doShiftClickTransfer(clickedSlotItem, 0, topRows * 9, false)) {
                            resultStack[0] = InvseeImpl.EMPTY_STACK;
                            return;
                        }
                    }

                    if (clickedSlotItem.isEmpty()) {
                        slot.set(InvseeImpl.EMPTY_STACK);
                    } else {
                        slot.d();
                    }
                }
                resultStack[0] = itemstack;
            };
            
            // Execute sync spectator task (blocks until done in Folia)
            executeSyncSpectator(task, null);
            return resultStack[0];
        } else {
            // Bukkit/Paper: execute directly (0 scheduler calls)
            ItemStack itemstack = InvseeImpl.EMPTY_STACK;
            Slot slot = slots.get(rawIndex);
            final int topRows = 6;

            if (slot != null && slot.hasItem()) {
                ItemStack clickedSlotItem = slot.getItem();

                itemstack = clickedSlotItem.cloneItemStack();
                if (rawIndex < topRows * 9) {
                    //clicked in the top inventory
                    if (!doShiftClickTransfer(clickedSlotItem, topRows * 9, slots.size(), true)) {
                        return InvseeImpl.EMPTY_STACK;
                    }
                } else {
                    //clicked in the bottom inventory
                    if (!doShiftClickTransfer(clickedSlotItem, 0, topRows * 9, false)) {
                        return InvseeImpl.EMPTY_STACK;
                    }
                }

                if (clickedSlotItem.isEmpty()) {
                    slot.set(InvseeImpl.EMPTY_STACK);
                } else {
                    slot.d();
                }
            }
            return itemstack;
        }
    }

    private boolean doShiftClickTransfer(ItemStack clickedSlotItem, int targetMinIndex, int targetMaxIndex, boolean topClicked) {
        //returns true is something if part of the clickedSlotItem was transferred, otherwise false
        return super.a(clickedSlotItem, targetMinIndex, targetMaxIndex, topClicked);
    }

    // ===== Magma Compatibility =====
    // https://github.com/Jannyboy11/InvSee-plus-plus/issues/43#issuecomment-1493377971

    //because this is being called from InvSee++, and Magma has a bug, we need to override this.
    public Containers<?> getType() {
        return Containers.GENERIC_9X6;
    }

    //idem
    public Slot getSlot(int rawIndex) {
        return super.getSlot(rawIndex);
    }

    //dito
    public void addSlotListener(ICrafting icrafting) {
        super.addSlotListener(icrafting);
    }

    public boolean func_75145_c(EntityHuman playerEntity) {
        return canUse(playerEntity);
    }

    public ItemStack func_184996_a(int a, int b, InventoryClickType clickType, EntityHuman playerEntity) {
        //clicked
        return a(a, b, clickType, playerEntity);
    }

    public void func_75134_a(EntityHuman playerEntity) {
        //removed
        b(playerEntity);
    }

    public ItemStack func_82846_b(EntityHuman playerEntity, int rawIndex) {
        return shiftClick(playerEntity, rawIndex);
    }

    /**
     * Get the region scheduler for the target entity.
     * {@return} the entity scheduler of the target, or {@code null} if not running on Folia.
     */
    @Override
    public @Nullable EntityScheduler getTargetEntityScheduler() {
        // In 1.16.5, Folia support is not natively implemented in the platform, but we can return null
        // or try to detect Folia if the platform is upgraded.
        // For now, we return null.
        return null;
    }

    /**
     * Get the region scheduler for the spectator entity.
     * {@return} the entity scheduler of the spectator, or {\@code null} if not running on Folia.
     */
    @Override
    public @Nullable EntityScheduler getSpectatorEntityScheduler() {
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
        com.janboerman.invsee.spigot.api.Scheduler scheduler = creationOptions.getPlugin().getApi().getScheduler();
        scheduler.executeSyncGlobal(task);
        if (retired != null) {
            scheduler.executeSyncGlobal(retired);
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
