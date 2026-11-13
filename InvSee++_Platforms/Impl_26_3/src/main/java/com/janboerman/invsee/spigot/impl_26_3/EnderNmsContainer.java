package com.janboerman.invsee.spigot.impl_26_3;

import com.janboerman.invsee.spigot.api.CreationOptions;
import com.janboerman.invsee.spigot.api.logging.DifferenceTracker;
import com.janboerman.invsee.spigot.api.logging.LogOptions;
import com.janboerman.invsee.spigot.api.logging.LogOutput;
import com.janboerman.invsee.spigot.api.target.Target;
import com.janboerman.invsee.spigot.api.template.EnderChestSlot;
import com.janboerman.invsee.spigot.api.template.Mirror;
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
import com.janboerman.invsee.spigot.api.Scheduler;
import com.janboerman.invsee.spigot.internal.transaction.InventoryTransaction;

class EnderNmsContainer extends AbstractContainerMenu {
	
	final Player player;
	final EnderNmsInventory top;
	final Inventory bottom;
	final String originalTitle;
	String title;

	final CreationOptions<EnderChestSlot> creationOptions;
	private final int topRows;	//in Purpur, this is not always 3.
	private EnderBukkitInventoryView bukkitView;
	final DifferenceTracker tracker;
	
	private static MenuType<?> determineMenuType(EnderNmsInventory inv) {
		return switch (inv.getContainerSize()) {
			case 9 -> MenuType.GENERIC_9x1;
			case 18 -> MenuType.GENERIC_9x2;
			case 27 -> MenuType.GENERIC_9x3;
			case 36 -> MenuType.GENERIC_9x4;
			case 45 -> MenuType.GENERIC_9x5;
			case 54 -> MenuType.GENERIC_9x6;
			default -> MenuType.GENERIC_9x3;
		};
	}

	private static final InventoryTransaction.Items<ItemStack> ITEMS = new InventoryTransaction.Items<ItemStack>() {
		@Override public boolean isEmpty(ItemStack stack) { return stack.isEmpty(); }

		@Override public ItemStack copy(ItemStack stack) { return stack.isEmpty() ? ItemStack.EMPTY : stack.copy(); }

		@Override public boolean matches(ItemStack one, ItemStack other) {
			if (one == other) return true;
			boolean oneEmpty = one.isEmpty(), otherEmpty = other.isEmpty();
			if (oneEmpty != otherEmpty) return false;
			if (oneEmpty) return true; //both are empty
			//expensive: compare the item stacks' data.
			org.bukkit.inventory.ItemStack bukkitOne = CraftItemStack.asBukkitCopy(one);
			org.bukkit.inventory.ItemStack bukkitTwo = CraftItemStack.asBukkitCopy(other);
			return bukkitOne.getAmount() == bukkitTwo.getAmount() && bukkitOne.isSimilar(bukkitTwo);
		}
	};

	private final InventoryTransaction<ItemStack> transaction;
	private final Object clickLock;

	/**
	 * Get the lock which serialises the clicks of two or more spectators that have this inventory open at the same
	 * time, for inventories which no region owns. Returns null when clicks do not need to be serialised.
	 * <p>
	 * This is the case on servers which use threaded regions (Folia) when the target player is offline: their
	 * inventory is then a detached copy which no region owns, so there is nothing to schedule - the changes can be
	 * applied straight away. Two spectators can still have that same inventory open at the same time though, so
	 * their clicks do need to be serialised against each other.
	 */
	private Object createClickLock() {
		if (player.getUUID().equals(top.targetPlayerUuid)) return null; //the spectating player *is* the target player: nobody else can touch their inventory.
		Plugin plugin = creationOptions.getPlugin();
		if (plugin == null) return null;
		Scheduler scheduler = Scheduler.findScheduler(plugin);
		if (scheduler == null || !scheduler.isFolia()) return null; //no threaded regions: the server has one thread.
		if (plugin.getServer().getPlayer(top.targetPlayerUuid) != null) return null; //the target player is online: the transaction replays the changes instead.
		return top.clickLock;
	}

	/**
	 * Creates the transaction helper which replays the changes made by clicks to the target player's inventory on
	 * the thread which owns that inventory. Returns null when the changes can be applied straight away, which is the
	 * case when the server does not use threaded regions, when the target player is offline, or when the spectating
	 * player is the target player. Exactly one of this method and {@link #createClickLock()} returns a non-null value.
	 */
	private InventoryTransaction<ItemStack> createTransaction() {
		if (clickLock != null) return null; //the target player is offline: the clicks are serialised instead.
		if (player.getUUID().equals(top.targetPlayerUuid)) return null; //the spectating player *is* the target player: their inventory is owned by this thread.
		Plugin plugin = creationOptions.getPlugin();
		if (plugin == null) return null;
		Scheduler scheduler = Scheduler.findScheduler(plugin);
		if (scheduler == null || !scheduler.isFolia()) return null; //no threaded regions: the server has one thread.
		return new InventoryTransaction<>(scheduler, top.targetPlayerUuid,
				new InventoryTransaction.Inventory<ItemStack>() {
					@Override public int size() { return top.getContainerSize(); }
					@Override public ItemStack get(int slot) { return top.getItem(slot); }
					@Override public void set(int slot, ItemStack item) { top.setItem(slot, item); }
					@Override public void markChanged() { top.setChanged(); }
				}, ITEMS);
	}


	private static Slot makeSlot(Mirror<EnderChestSlot> mirror, EnderNmsInventory top, int positionIndex, int magicX, int magicY, ItemStack inaccessiblePlaceholder) {
		final EnderChestSlot place = mirror.getSlot(positionIndex);

		if (place == null) {
			return new InaccessibleSlot(inaccessiblePlaceholder, top, positionIndex, magicX, magicY);
		} else {
			final int referringTo = place.ordinal();
			return new Slot(top, referringTo, magicX, magicY);
		}
	}

	// decorate clicked method for tracking/logging
	@Override
	public void clicked(int i, int j, ContainerInput inventoryclicktype, Player entityhuman) {
		final List<org.bukkit.inventory.ItemStack> contentsBefore;
		if (tracker != null) {
			contentsBefore = top.getContents().stream().map(CraftItemStack::asBukkitCopy).toList();
		} else {
			contentsBefore = null;
		}

		Runnable handleClick = () -> {
			super.clicked(i, j, inventoryclicktype, entityhuman);

			if (tracker != null) {
				//note: this happens *inside* the transaction, because the transaction undoes the changes which the
				//click just made to the target player's inventory.
				List<org.bukkit.inventory.ItemStack> contentsAfter = top.getContents().stream().map(CraftItemStack::asBukkitCopy).toList();
				tracker.onClick(contentsBefore, contentsAfter);
			}
		};

		if (transaction != null) {
			// Folia: the target player's inventory is owned by the thread of the region the target player is in,
			// whereas this method runs on the thread of the region the spectating player is in.
			// Therefore the click is computed against a snapshot of the target player's inventory, and the
			// resulting changes are replayed on the scheduler of the target player.
			// See https://github.com/Jannyboy11/InvSee-plus-plus/issues/58
			transaction.transact(handleClick);
		} else if (clickLock != null) {
			// Folia: the target player is offline, so their inventory is a detached copy which no region owns and
			// there is nothing to schedule. Multiple spectators can have it open at the same time though, so
			// their clicks are serialised. See https://github.com/Jannyboy11/InvSee-plus-plus/issues/58
			synchronized (clickLock) {
				handleClick.run();
			}
		} else {
			// No threaded regions: the server has a single thread, so the click can be applied straight away.
			// This is also the case when the spectating player is the target player.
			handleClick.run();
		}
	}

	// decorate removed method for tracking/logging
	@Override
	public void removed(Player entityhuman) {
		super.removed(entityhuman);

		if (tracker != null && Objects.equals(entityhuman, player)) {
			tracker.onClose();
		}
	}

	EnderNmsContainer(int containerId, EnderNmsInventory nmsInventory, Inventory playerInventory, Player player, CreationOptions<EnderChestSlot> creationOptions) {
		super(determineMenuType(nmsInventory), containerId);
		
		this.topRows = nmsInventory.getContainerSize() / 9;
		this.player = player;
		this.top = nmsInventory;
		this.bottom = playerInventory;

		this.creationOptions = creationOptions;
		this.clickLock = createClickLock();
		this.transaction = createTransaction();
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

		//top inventory slots
		for (int yPos = 0; yPos < topRows; yPos++) {
			for (int xPos = 0; xPos < 9; xPos++) {
				int index = xPos + yPos * 9;
				int magicX = 8 + xPos * 18;
				int magicY = 18 + yPos * 18;

				addSlot(makeSlot(mirror, top, index, magicX, magicY, CraftItemStack.asNMSCopy(creationOptions.getPlaceholderPalette().inaccessible())));
			}
		}
		
		//bottom inventory slots
		int magicAddY = (topRows - 4 /*4 because the bottom inventory has 4 rows*/) * 18;
		
		//player 'storage'
		for (int yPos = 1; yPos < 4; yPos++) {
			for (int xPos = 0; xPos < 9; xPos++) {
				int index = xPos + yPos * 9;
				int magicX = 8 + xPos * 18;
				int magicY = 103 + yPos * 18 + magicAddY;
				addSlot(new Slot(bottom, index, magicX, magicY));
			}
		}
		
		//player 'hotbar' (yPos = 0)
		for (int xPos = 0; xPos < 9; xPos++) {
			int index = xPos;
			int magicX = 8 + xPos * 18;
			int magicY = 161 + magicAddY;
			addSlot(new Slot(bottom, index, magicX, magicY));
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
	public ItemStack quickMoveStack(Player player, int rawIndex) {
		//returns ItemStack.EMPTY when we are done transferring the itemstack on the rawIndex
        //remember that we are called from inside the body of a loop!

		ItemStack itemStack = ItemStack.EMPTY;
		Slot slot = getSlot(rawIndex);
		
		if (slot != null && slot.hasItem()) {
			ItemStack clickedSlotItem = slot.getItem();
			
			itemStack = clickedSlotItem.copy();
			if (rawIndex < topRows * 9) {
				//clicked in the top inventory
				if (!moveItemStackTo(clickedSlotItem, topRows * 9, slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				//clicked in the bottom inventory
				if (!moveItemStackTo(clickedSlotItem, 0, topRows * 9, false)) {
					return ItemStack.EMPTY;
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

	public String title() {
		return title != null ? title : originalTitle;
	}

}
