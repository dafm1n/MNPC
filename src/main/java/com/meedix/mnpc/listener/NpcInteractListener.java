package com.meedix.mnpc.listener;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.event.NpcClickType;
import com.meedix.mnpc.api.event.NpcInteractEvent;
import com.meedix.mnpc.core.NpcRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Translates clicks on packet-only entities into {@link NpcInteractEvent}s.
 *
 * <p>MNPC entities exist purely client-side, so clicking them sends a
 * {@code ServerboundInteractPacket} with an entity id unknown to the server.
 * Paper surfaces exactly this case as {@link PlayerUseUnknownEntityEvent}
 * (fired on the main thread), which means no netty pipeline injection is
 * needed for interaction handling.</p>
 *
 * <p>The client sends INTERACT and INTERACT_AT (and both hands) for a single
 * right click; events are de-duplicated per player/NPC/tick so consumers
 * receive exactly one {@link NpcInteractEvent} per physical click.</p>
 */
public final class NpcInteractListener implements Listener {

    private final NpcRegistry registry;
    /** player uuid -> (npc entity id -> last handled tick). */
    private final Map<UUID, Map<Integer, Integer>> lastInteraction = new ConcurrentHashMap<>();

    /**
     * @param registry NPC registry for entity-id lookups
     */
    public NpcInteractListener(NpcRegistry registry) {
        this.registry = registry;
    }

    /** Handles clicks on unknown (= packet-only) entities. */
    @EventHandler
    public void onUseUnknownEntity(PlayerUseUnknownEntityEvent event) {
        Npc npc = registry.byEntityId(event.getEntityId()).orElse(null);
        if (npc == null) {
            return;
        }
        // Right clicks arrive once per hand; only handle the main hand.
        if (!event.isAttack() && event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (isDuplicate(player, npc)) {
            return;
        }
        NpcClickType clickType = NpcClickType.of(event.isAttack(), player.isSneaking());
        Bukkit.getPluginManager().callEvent(new NpcInteractEvent(npc, player, clickType));
    }

    /** Forgets de-duplication state of quitting players. */
    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        lastInteraction.remove(event.getPlayer().getUniqueId());
    }

    private boolean isDuplicate(Player player, Npc npc) {
        int currentTick = Bukkit.getCurrentTick();
        Map<Integer, Integer> perNpc = lastInteraction
                .computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>());
        Integer lastTick = perNpc.put(npc.getEntityId(), currentTick);
        return lastTick != null && lastTick == currentTick;
    }
}
