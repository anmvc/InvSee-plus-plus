package com.janboerman.invsee.spigot.internal.inventory;

import com.janboerman.invsee.spigot.api.CreationOptions;
import com.janboerman.invsee.spigot.api.SpectatorInventory;
import com.janboerman.invsee.utils.UUIDHelper;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class AbstractNmsInventory<Slot, Bukkit extends SpectatorInventory<Slot>, NMS extends AbstractNmsInventory<Slot, Bukkit, NMS>> implements ShallowCopy<NMS> {

    //NOTE: despite the many similarities between the different NMS inventory implementations,
    //NOTE: we can only abstract out the non-nms parts!

    public final UUID targetPlayerUuid;
    public final String targetPlayerName;

    /**
     * Lock which is used to serialise the clicks of two or more spectators which have this inventory open at the same
     * time, in the case where no region owns this inventory.
     * <p>
     * On servers which use threaded regions (Folia) the inventory of an <em>offline</em> player is a detached copy
     * which no region owns, so clicks on it do not need to be scheduled. Multiple spectators can still have that same
     * inventory open at the same time though (InvSee++ re-uses the inventory of an offline player which is already
     * open), so their clicks do need to be serialised.
     */
    public final Object clickLock = new Object();

    public final CreationOptions<Slot> creationOptions;
    private Bukkit bukkit;

    protected int maxStack; //can't abstract out the getters and setters because of remapping.
    private final List<HumanEntity> transaction = new ArrayList<>(1);
    protected InventoryHolder owner;

    protected AbstractNmsInventory(UUID targetPlayerUuid, String targetPlayerName, CreationOptions<Slot> creationOptions) {
        this.targetPlayerUuid = UUIDHelper.copy(targetPlayerUuid);
        this.targetPlayerName = targetPlayerName;
        this.maxStack = defaultMaxStack();
        this.creationOptions = creationOptions;
    }

    protected abstract Bukkit createBukkit();

    public Bukkit bukkit() {
        return bukkit == null ? bukkit = createBukkit() : bukkit;
    }

    public void onOpen(HumanEntity who) {
        transaction.add(who);
    }

    public void onClose(HumanEntity who) {
        transaction.remove(who);
    }

    public List<HumanEntity> getViewers() {
        return transaction;
    }

    public InventoryHolder getOwner() {
        return owner;
    }

    public Location getLocation() {
        return null;
    }

}
