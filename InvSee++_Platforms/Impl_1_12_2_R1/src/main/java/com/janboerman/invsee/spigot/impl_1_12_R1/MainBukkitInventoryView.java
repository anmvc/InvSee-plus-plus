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
    public void setItem(int slot, ItemStack item) {
        // FOLIASYNC: schedule task ONLY if we are on Folia (target/spectator schedulers exist)
        // Otherwise, execute directly on the calling thread (Bukkit/Paper fallback) to minimize scheduler calls.
        net.minecraft.server.v1_12_R1.ItemStack stack = CraftItemStack.asNMSCopy(item);
        
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
                net.minecraft.server.v1_12_R1.ItemStack nmsStack;
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
            net.minecraft.server.v1_12_R1.ItemStack nmsStack;
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
