package com.janboerman.invsee.spigot.impl_1_12_R1;

import com.janboerman.invsee.spigot.api.CreationOptions;
import com.janboerman.invsee.spigot.api.logging.DifferenceTracker;
import com.janboerman.invsee.spigot.api.logging.LogOptions;
import com.janboerman.invsee.spigot.api.logging.LogOutput;
import com.janboerman.invsee.spigot.api.target.Target;
import com.janboerman.invsee.spigot.api.template.Mirror;
import com.janboerman.invsee.spigot.api.template.PlayerInventorySlot;
import com.janboerman.invsee.spigot.internal.inventory.AbstractNmsInventory;
import com.janboerman.invsee.spigot.internal.inventory.ShallowCopy;
import com.janboerman.invsee.spigot.internal.inventory.StandardSpectatorInventory;
import com.janboerman.invsee.spigot.internal.inventory.Wrapper;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import net.minecraft.server.v1_12_R1.Container;
import net.minecraft.server.v1_12_R1.EntityHuman;
import net.minecraft.server.v1_12_R1.InventoryClickType;
import net.minecraft.server.v1_12_R1.ItemStack;
import net.minecraft.server.v1_12_R1.PlayerInventory;
import net.minecraft.server.v1_12_R1.Slot;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

class MainNmsContainer extends Container implements AbstractNmsInventory<PlayerInventorySlot, MainBukkitInventory, MainNmsContainer>, StandardSpectatorInventory<PlayerInventorySlot, MainNmsContainer, MainNmsContainer>, Wrapper<MainNmsContainer, MainNmsContainer> {

    final EntityHuman player;
    final MainNmsInventory top;
    final PlayerInventory bottom;
    final String title;
    final CreationOptions<PlayerInventorySlot> creationOptions;

    private final boolean spectatingOwnInventory;
    private MainBukkitInventoryView bukkitView;
    final DifferenceTracker tracker;

    private static Slot makeSlot(Mirror<PlayerInventorySlot> mirror, boolean spectatingOwnInventory, MainNmsInventory top, int positionIndex, int magicX, int magicY, ItemStack inaccessiblePlaceholder) {
        final PlayerInventorySlot place = mirror.getSlot(positionIndex);

        if (place == null) {
            return new InaccessiblePlaceholderSlot(inaccessiblePlaceholder, top, positionIndex, magicX, magicY);
        } else if (place.isContainer()) {
            final int referringTo = place.ordinal() - PlayerInventorySlot.CONTAINER_00.ordinal();
            return new Slot(top, referringTo, magicX, magicY); //magicX and magicY correct here? it seems to work though.
        } else if (place == PlayerInventorySlot.ARMOUR_BOOTS) {
            final int referringTo = 36;
            return new BootsSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place == PlayerInventorySlot.ARMOUR_LEGGINGS) {
            final int referringTo = 37;
            return new LeggingsSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place == PlayerInventorySlot.ARMOUR_CHESTPLATE) {
            final int referringTo = 38;
            return new ChestplateSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place == PlayerInventorySlot.ARMOUR_HELMET) {
            final int referringTo = 39;
            return new HelmetSlot(top, referringTo, magicX, magicY); //idem?
        } else if (place.isPersonal()) {
            final int referringTo = place.ordinal() - PlayerInventorySlot.PERSONAL_00.ordinal() + 45;
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
        
        ItemStack result;
        if (targetScheduler != null && spectatorScheduler != null) {
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
        
        if (targetScheduler != null && spectatorScheduler != null) {
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

    MainNmsContainer(int containerId, MainNmsInventory nmsInventory, PlayerInventory playerInventory, EntityHuman player, CreationOptions<PlayerInventorySlot> creationOptions) {
        this.windowId = containerId;

        this.top = nmsInventory;
        this.bottom = playerInventory;
        this.player = player;
        this.spectatingOwnInventory = player.getUniqueID().equals(nmsInventory.targetPlayerUuid);

        //creation options
        this.creationOptions = creationOptions;
        //title
        this.title = creationOptions.getTitle().titleFor(Target.byGameProfile(nmsInventory.targetPlayerUuid, nmsInventory.targetPlayerName));
        //mirror
        Mirror<PlayerInventorySlot> mirror = creationOptions.getMirror();
        //logging
        LogOptions logOptions = creationOptions.getLogOptions();
        Plugin plugin = creationOptions.getPlugin();
        if (!LogOptions.isEmpty(logOptions)) {
            this.tracker = new DifferenceTracker(
                    LogOutput.make(plugin, player.getUniqueID(), player.getName(), Target.byGameProfile(nmsInventory.targetPlayerUuid, nmsInventory.targetPlayerName), logOptions),
                    logOptions.getGranularity());
            this.tracker.onOpen();
        } else {
            this.tracker = null;
        }

        //top inventory slots
        for (int yPos = 0; yPos < 6; yPos++) {
            for (int xPos = 0; xPos < 9; xPos++) {
                int index = xPos + yPos * 9;
                int magicX = 8 + xPos * 18;
                int magicY = 18 + yPos * 18;
                a(makeSlot(mirror, spectatingOwnInventory, top, index, magicX, magicY, CraftItemStack.asNMSCopy(creationOptions.getPlaceholderPalette().inaccessible())));
            }
        }

        //bottom inventory slots
        int magicAddY = (6 /*6 for 6 rows of the top inventory*/ - 4 /*4 for 4 rows of the bottom inventory??*/) * 18;

        //player 'storage'
        for (int yPos = 1; yPos < 4; yPos++) {
            for (int xPos = 0; xPos < 9; xPos++) {
                int index = xPos + yPos * 9;
                int magicX = 8 + xPos * 18;
                int magicY = 103 + yPos * 18 + magicAddY;
                a(new Slot(playerInventory, index, magicX, magicY));
            }
        }

        //player 'hotbar'
        for (int xPos = 0; xPos < 9; xPos++) {
            int index = xPos;
            int magicX = 8 + xPos * 18;
            int magicY = 161 + magicAddY;
            a(new Slot(playerInventory, index, magicX, magicY));
        }
    }

    @Override
    public MainBukkitInventoryView getBukkitView() {
        if (bukkitView == null) {
            bukkitView = new MainBukkitInventoryView(this);
        }
        return bukkitView;
    }

    @Override
    public boolean canUse(EntityHuman entityHuman) {
        return true;
    }

    @Override
    public ItemStack shiftClick(EntityHuman entityhuman, int rawIndex) {
        //returns EMPTY_STACK when we are done transferring the itemstack on the rawIndex
        //remember that we are called inside the body of a loop!

        if (spectatingOwnInventory)
            return InvseeImpl.EMPTY_STACK;

        // FOLIASYNC: schedule task ONLY if we are on Folia
        EntityScheduler targetScheduler = getTargetEntityScheduler();
        EntityScheduler spectatorScheduler = getSpectatorEntityScheduler();
        
        if (targetScheduler != null && spectatorScheduler != null) {
            final ItemStack[] resultStack = { InvseeImpl.EMPTY_STACK };
            final Runnable task = () -> {
                ItemStack itemstack = InvseeImpl.EMPTY_STACK;
                Slot slot = this.slots.get(rawIndex);
                final int topRows = 6;

                if (slot != null && slot.hasItem()) {
                    ItemStack clickedSlotItem = slot.getItem();

                    itemstack = clickedSlotItem.cloneItemStack();
                    if (rawIndex < topRows * 9) {
                        //clicked in the top inventory
                        if (!doShiftClickTransfer(clickedSlotItem, topRows * 9, this.slots.size(), true)) {
                            itemstack = InvseeImpl.EMPTY_STACK;
                        }
                    } else {
                        //clicked in the bottom inventory
                        if (!doShiftClickTransfer(clickedSlotItem, 0, topRows * 9, false)) {
                            itemstack = InvseeImpl.EMPTY_STACK;
                        }
                    }

                    if (clickedSlotItem.isEmpty()) {
                        slot.set(InvseeImpl.EMPTY_STACK);
                    } else {
                        slot.f();
                    }
                }
                resultStack[0] = itemstack;
            };
            executeSyncSpectator(task, null);
            return resultStack[0];
        } else {
            // Bukkit/Paper: execute directly (0 scheduler calls)
            ItemStack itemstack = InvseeImpl.EMPTY_STACK;
            Slot slot = this.slots.get(rawIndex);
            final int topRows = 6;

            if (slot != null && slot.hasItem()) {
                ItemStack clickedSlotItem = slot.getItem();

                itemstack = clickedSlotItem.cloneItemStack();
                if (rawIndex < topRows * 9) {
                    //clicked in the top inventory
                    if (!doShiftClickTransfer(clickedSlotItem, topRows * 9, this.slots.size(), true)) {
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
                    slot.f();
                }
            }
            return itemstack;
        }
    }

    private boolean doShiftClickTransfer(ItemStack clickedSlotItem, int targetMinIndex, int targetMaxIndex, boolean topClicked) {
        //returns true is something if part of the clickedSlotItem was transferred, otherwise false
        return super.a(clickedSlotItem, targetMinIndex, targetMaxIndex, topClicked);
    }

    /**
     * Get the region scheduler for the target entity.
     * {\@return} the entity scheduler of the target, or {\@code null} if not running on Folia.
     */
    @Override
    public {\@Nullable} EntityScheduler getTargetEntityScheduler() {
        // In 1.12.2, Folia support is not natively implemented in the platform, but we can return null
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
        com.janboerman.invsee.spigot.api.Scheduler scheduler = creationOptions.getPlugin().getApi().getScheduler();
        scheduler.executeSyncGlobal(task);
        if (retired != null) {
            scheduler.executeSyncGlobal(retired);
        }
    }

    @Override
    public MainBukkitInventory getBukkit() {
        return getBukkitView();
    }

    @Override
    public MainNmsContainer getInventory() {
        return this;
    }

    @Override
    public MainNmsContainer shallowCopyFrom(MainBukkitInventory source) {
        // This is a placeholder for shallow copy implementation if needed
        return this;
    }
}
