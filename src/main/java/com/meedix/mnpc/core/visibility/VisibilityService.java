package com.meedix.mnpc.core.visibility;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.trait.Trait;
import com.meedix.mnpc.core.NpcAccess;
import com.meedix.mnpc.nms.PacketAdapter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Decides which players see which NPCs.
 *
 * <p>Distance checks run on the main thread (cheap squared-distance math),
 * while spawn packet preparation and dispatch are offloaded to a small async
 * executor — netty {@code Connection#send} is thread-safe, so packet
 * construction never blocks the main thread even with hundreds of joins.</p>
 */
public final class VisibilityService {

    private final Plugin plugin;
    private final PacketAdapter adapter;
    /** Virtual threads: cheap, and spawn bursts (mass join) parallelise well. */
    private final ExecutorService spawnExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * @param plugin  owning plugin (used for trait callbacks scheduling)
     * @param adapter packet adapter
     */
    public VisibilityService(Plugin plugin, PacketAdapter adapter) {
        this.plugin = plugin;
        this.adapter = adapter;
    }

    /**
     * Recomputes visibility of one NPC for one player based on world and
     * distance. Called by the central tick manager and on player events.
     *
     * @param npc    the NPC to evaluate
     * @param player the candidate viewer
     */
    public void update(Npc npc, Player player) {
        boolean shouldSee = shouldSee(npc, player);
        boolean sees = npc.isVisibleTo(player);
        if (shouldSee && !sees) {
            show(npc, player);
        } else if (!shouldSee && sees) {
            hide(npc, player);
        }
    }

    /**
     * Spawns the NPC for the player (packets async, trait callbacks sync).
     *
     * @param npc    the NPC to show
     * @param player the new viewer
     */
    public void show(Npc npc, Player player) {
        Set<Player> viewers = NpcAccess.viewers(npc);
        if (!viewers.add(player)) {
            return;
        }
        spawnExecutor.execute(() -> {
            if (player.isOnline() && !npc.isRemoved()) {
                adapter.spawnNpc(player, npc);
            }
        });
        for (Trait trait : NpcAccess.traits(npc)) {
            trait.onSpawn(player);
        }
    }

    /**
     * Despawns the NPC for the player.
     *
     * @param npc    the NPC to hide
     * @param player the leaving viewer
     */
    public void hide(Npc npc, Player player) {
        Set<Player> viewers = NpcAccess.viewers(npc);
        if (!viewers.remove(player)) {
            return;
        }
        if (player.isOnline()) {
            adapter.despawnNpc(player, npc);
        }
        for (Trait trait : NpcAccess.traits(npc)) {
            trait.onDespawn(player);
        }
    }

    /**
     * Drops a quitting player from all viewer sets without sending packets.
     *
     * @param npcs   all registered NPCs
     * @param player the quitting player
     */
    public void handleQuit(Iterable<? extends Npc> npcs, Player player) {
        for (Npc npc : npcs) {
            if (NpcAccess.viewers(npc).remove(player)) {
                for (Trait trait : NpcAccess.traits(npc)) {
                    trait.onDespawn(player);
                }
            }
        }
    }

    /**
     * Re-evaluates all NPCs for a player who changed worlds. Clients forget
     * all entities on world change, so viewer state is reset first.
     *
     * @param npcs   all registered NPCs
     * @param player the player who switched worlds
     */
    public void handleWorldChange(Iterable<? extends Npc> npcs, Player player) {
        for (Npc npc : npcs) {
            if (NpcAccess.viewers(npc).remove(player)) {
                for (Trait trait : NpcAccess.traits(npc)) {
                    trait.onDespawn(player);
                }
            }
            update(npc, player);
        }
    }

    /** Despawns the NPC for every viewer (NPC removal). */
    public void hideFromAll(Npc npc) {
        for (Player viewer : List.copyOf(NpcAccess.viewers(npc))) {
            hide(npc, viewer);
        }
    }

    /** Stops the async spawn executor. Call on plugin disable. */
    public void shutdown() {
        spawnExecutor.shutdownNow();
    }

    private boolean shouldSee(Npc npc, Player player) {
        if (npc.isRemoved() || !player.isOnline()) {
            return false;
        }
        Location npcLocation = npc.getLocation();
        if (!player.getWorld().equals(npcLocation.getWorld())) {
            return false;
        }
        double radius = npc.getViewRadius();
        return player.getLocation().distanceSquared(npcLocation) <= radius * radius;
    }
}
