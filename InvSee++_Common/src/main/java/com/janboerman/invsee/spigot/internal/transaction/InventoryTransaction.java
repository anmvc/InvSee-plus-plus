package com.janboerman.invsee.spigot.internal.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.janboerman.invsee.spigot.api.Scheduler;

/**
 * Optimistic, snapshot-based (STM-style) transaction support for the inventories which are shown inside a
 * {@linkplain com.janboerman.invsee.spigot.api.SpectatorInventoryView spectator inventory view}.
 * <p>
 * InvSee++ combines the items of the spectator (the 'bottom' inventory) and the items of the target player (the 'top'
 * inventory) into one window. On servers which use threaded regions (Folia), those two inventories are owned by two
 * different threads, while a click inside the window - which is handled by the server's container/menu logic - reads
 * from and writes to both of them at the same time. That is a data race: see
 * <a href="https://github.com/Jannyboy11/InvSee-plus-plus/issues/58">issue #58</a>.
 * <p>
 * This class solves that problem in the following way:
 * <ol>
 * <li>A snapshot is taken of the target player's inventory.</li>
 * <li>The click is applied by the server's container/menu logic. The changes it makes to the spectator's own
 *     inventory (and to the item stack on the spectator's cursor) are kept, because the spectator's thread owns
 *     them anyway.</li>
 * <li>The difference between the snapshot and the result of the click is computed. Only the slots which actually
 *     changed are part of the difference.</li>
 * <li>The changes which the click made to the target player's inventory are undone. After this step, the target
 *     player's inventory holds exactly the same values as it did before the click.</li>
 * <li>Finally, the difference is replayed on the scheduler of the target player - the only thread which is allowed to
 *     touch the target player's inventory.</li>
 * </ol>
 * Because the spectator's own inventory (and the item stack on their cursor) is modified by the click itself, the
 * difference is always applied - there is no 'abort' path. If the target player modified one of the changed slots in
 * the meantime, then the spectator's change wins; the target player's change for that slot is overwritten. This is no
 * different from what would happen if both players were on the same thread, and it can neither duplicate nor delete
 * items.
 * <p>
 * <b>Note:</b> step 4 undoes the writes of the click by writing to the target player's inventory from the spectator's
 * thread. It only ever touches slots which the click itself already wrote to, and every one of those slots is
 * overwritten again by step 5, so the target player cannot lose any item because of it. It is nevertheless the reason
 * why this class is a mitigation rather than a silver bullet: the click in step 2 still reads the target player's
 * inventory from the spectator's thread. Making the view read from a private copy instead would stop the view from
 * showing the changes which the target player makes to their own inventory, which is why this class does not do that.
 * <p>
 * This class is thread-safe: {@link #transact(Runnable)} is called by the thread of the spectator, while the changes
 * are committed by the thread of the target player.
 *
 * @param <I> the item stack type
 */
public final class InventoryTransaction<I> {

    /** Slot-level access to an inventory. */
    public interface Inventory<I> {

        /** Get the amount of slots of this inventory. */
        int size();

        /** Get the item stack in the given slot. */
        I get(int slot);

        /** Set the item stack in the given slot. Must not notify the inventory's owner about the change. */
        void set(int slot, I item);

        /**
         * Notify the inventory's owner that its contents changed.
         * Called on the thread which owns the inventory, after changes have been committed.
         */
        default void markChanged() { }

    }

    /** Item stack type-specific operations. */
    public interface Items<I> {

        /** Get whether the item stack is empty. */
        boolean isEmpty(I item);

        /** Get whether the two item stacks are considered to be equal. */
        boolean matches(I one, I other);

        /** Create a copy of the item stack. */
        I copy(I item);

    }

    private final Scheduler scheduler;
    private final UUID targetPlayerId;
    private final boolean targetPlayerOnline;
    private final Inventory<I> targetInventory;
    private final Items<I> items;

    private final List<SlotChange<I>> pending = new ArrayList<>();
    private boolean commitScheduled;

    /**
     * Create a new transaction helper.
     *
     * @param scheduler the scheduler used to schedule tasks on the target player's thread
     * @param targetPlayerId the unique ID of the target player
     * @param targetPlayerOnline whether the target player is online
     * @param targetInventory the target player's inventory
     * @param items the item stack operations
     */
    public InventoryTransaction(Scheduler scheduler, UUID targetPlayerId, boolean targetPlayerOnline,
                                Inventory<I> targetInventory, Items<I> items) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler cannot be null");
        this.targetPlayerId = Objects.requireNonNull(targetPlayerId, "targetPlayerId cannot be null");
        this.targetPlayerOnline = targetPlayerOnline;
        this.targetInventory = Objects.requireNonNull(targetInventory, "targetInventory cannot be null");
        this.items = Objects.requireNonNull(items, "items cannot be null");
    }

    /**
     * Run the given action against a snapshot of the target player's inventory, and schedule the resulting changes
     * to be applied on the target player's own thread.
     * <p>
     * Must be called from the thread which owns the view (i.e. the thread of the spectating player).
     *
     * @param action the action (usually: the click which is being handled)
     */
    public void transact(Runnable action) {
        Objects.requireNonNull(action, "action cannot be null");

        //1. take a snapshot of the target player's inventory.
        final int size = targetInventory.size();
        @SuppressWarnings("unchecked")
        I[] before = (I[]) new Object[size];
        for (int slot = 0; slot < size; slot++) {
            I item = targetInventory.get(slot);
            before[slot] = items.isEmpty(item) ? item : items.copy(item);
        }

        //2. apply the action. this only touches state which is owned by the calling thread, plus the target
        //   player's inventory (which we are going to undo in step 4).
        action.run();

        //3. compute the difference.
        List<SlotChange<I>> changes = null;
        for (int slot = 0; slot < size; slot++) {
            I oldItem = before[slot];
            I newItem = targetInventory.get(slot);
            if (items.matches(oldItem, newItem)) continue;
            if (changes == null) changes = new ArrayList<>(4);
            changes.add(new SlotChange<>(slot, items.isEmpty(newItem) ? newItem : items.copy(newItem)));
        }

        if (changes == null) return; //nothing changed: we're done.

        //4. undo the changes which the action made to the target player's inventory, so that the target player's
        //   thread is the only thread which ever writes to it.
        for (SlotChange<I> change : changes) {
            targetInventory.set(change.slot, before[change.slot]);
        }

        //5. schedule the changes to be applied on the target player's own thread.
        boolean schedule;
        synchronized (pending) {
            for (SlotChange<I> change : changes) pending.add(change);
            schedule = !commitScheduled;
            commitScheduled = true;
        }
        if (schedule) scheduleCommit();
    }

    private void scheduleCommit() {
        Runnable commit = new Runnable() {
            @Override
            public void run() {
                commitPending();
            }
        };

        try {
            if (targetPlayerOnline) {
                //the target player is online: only their own entity scheduler may touch their inventory.
                //if the entity is retired in the meantime, then we simply apply the changes on the global region.
                scheduler.executeSyncPlayer(targetPlayerId, commit, commit);
            } else {
                //the target player's inventory is not owned by any entity: use the global region so that all
                //transactions for this inventory are serialised with respect to each other.
                scheduler.executeSyncGlobal(commit);
            }
        } catch (RuntimeException e) {
            //the task could not be scheduled (for instance because the plugin was disabled).
            //do not lose the changes: apply them right away.
            commitPending();
        }
    }

    private void commitPending() {
        //keep going until everything which has been added in the meantime has been committed as well:
        //in the meantime, transact(Runnable) sees that a commit is already scheduled and adds its changes to
        //'pending' instead of scheduling a second commit.
        while (true) {
            List<SlotChange<I>> changes;
            synchronized (pending) {
                if (pending.isEmpty()) {
                    commitScheduled = false;
                    return;
                }
                changes = new ArrayList<>(pending);
                pending.clear();
            }

            for (SlotChange<I> change : changes) {
                targetInventory.set(change.slot, change.item);
            }
            targetInventory.markChanged();
        }
    }

    private static final class SlotChange<I> {

        final int slot;
        final I item;

        SlotChange(int slot, I item) {
            this.slot = slot;
            this.item = item;
        }

    }

}
