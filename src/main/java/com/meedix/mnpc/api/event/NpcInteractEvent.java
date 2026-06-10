package com.meedix.mnpc.api.event;

import com.meedix.mnpc.api.Npc;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player clicks an NPC. Always called on the main thread.
 *
 * <pre>{@code
 * @EventHandler
 * public void onNpcClick(NpcInteractEvent event) {
 *     if (event.getClickType() == NpcClickType.RIGHT_CLICK) {
 *         event.getPlayer().sendMessage("Hello from " + event.getNpc().getName());
 *     }
 * }
 * }</pre>
 */
public class NpcInteractEvent extends Event implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Npc npc;
    private final Player player;
    private final NpcClickType clickType;
    private boolean cancelled;

    /**
     * @param npc       the clicked NPC
     * @param player    the clicking player
     * @param clickType the resolved click type
     */
    public NpcInteractEvent(Npc npc, Player player, NpcClickType clickType) {
        this.npc = npc;
        this.player = player;
        this.clickType = clickType;
    }

    /** @return the clicked NPC. */
    public Npc getNpc() {
        return npc;
    }

    /** @return the player who clicked. */
    public Player getPlayer() {
        return player;
    }

    /** @return the click type (left/right, with or without shift). */
    public NpcClickType getClickType() {
        return clickType;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    /** @return the static Bukkit handler list. */
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }
}
