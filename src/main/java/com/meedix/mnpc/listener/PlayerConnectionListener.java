package com.meedix.mnpc.listener;

import com.meedix.mnpc.core.NpcRegistry;
import com.meedix.mnpc.core.visibility.VisibilityService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Keeps NPC visibility in sync with player lifecycle events. Regular
 * movement is handled by the sharded visibility scan in the tick manager —
 * deliberately not by {@code PlayerMoveEvent}, which fires far too often.
 */
public final class PlayerConnectionListener implements Listener {

    private final NpcRegistry registry;
    private final VisibilityService visibility;

    /**
     * @param registry   NPC registry
     * @param visibility visibility service
     */
    public PlayerConnectionListener(NpcRegistry registry, VisibilityService visibility) {
        this.registry = registry;
        this.visibility = visibility;
    }

    /** Shows nearby NPCs to a joining player. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (var npc : registry.all()) {
            visibility.update(npc, player);
        }
    }

    /** Cleans viewer sets when a player quits (no packets needed). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        visibility.handleQuit(registry.all(), event.getPlayer());
    }

    /** Re-evaluates all NPCs after a world change (client forgot everything). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        visibility.handleWorldChange(registry.all(), event.getPlayer());
    }

    /** Re-evaluates visibility instantly after long-range teleports. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        // Runs next tick: the player's location is updated after the event.
        player.getServer().getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()), () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    for (var npc : registry.all()) {
                        visibility.update(npc, player);
                    }
                });
    }
}
