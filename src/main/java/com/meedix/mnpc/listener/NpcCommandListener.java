package com.meedix.mnpc.listener;

import com.meedix.mnpc.api.event.NpcClickType;
import com.meedix.mnpc.api.event.NpcInteractEvent;
import com.meedix.mnpc.trait.CommandTrait;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Runs the {@link CommandTrait} command when a player right-clicks an NPC.
 *
 * <p>{@link NpcInteractEvent} is always fired on the main thread, so the
 * command executes synchronously with the player's own permissions
 * (see {@link CommandTrait#execute}).</p>
 */
public final class NpcCommandListener implements Listener {

    /** Executes the attached command on (shift) right click. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onNpcInteract(NpcInteractEvent event) {
        NpcClickType click = event.getClickType();
        if (click != NpcClickType.RIGHT_CLICK && click != NpcClickType.SHIFT_RIGHT_CLICK) {
            return;
        }
        event.getNpc().getTrait(CommandTrait.class)
                .ifPresent(trait -> trait.execute(event.getPlayer()));
    }
}
