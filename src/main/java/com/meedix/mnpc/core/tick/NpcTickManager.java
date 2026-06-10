package com.meedix.mnpc.core.tick;

import com.meedix.mnpc.api.trait.Trait;
import com.meedix.mnpc.core.NpcAccess;
import com.meedix.mnpc.core.NpcRegistry;
import com.meedix.mnpc.core.visibility.VisibilityService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/**
 * The single heartbeat of the NPC engine.
 *
 * <p>Exactly one repeating task drives <em>all</em> NPCs — never one task per
 * NPC. Trait ticks run every tick; the much more expensive visibility scan is
 * sharded over {@code VISIBILITY_INTERVAL} ticks by entity id, so with 1000+
 * NPCs each tick only checks a fraction of all NPC/player pairs.</p>
 */
public final class NpcTickManager {

    /** Visibility of each NPC is re-evaluated once per this many ticks. */
    private static final int VISIBILITY_INTERVAL = 10;

    private final Plugin plugin;
    private final NpcRegistry registry;
    private final VisibilityService visibility;

    private BukkitTask task;
    private int tickCounter;

    /**
     * @param plugin     owning plugin
     * @param registry   NPC registry
     * @param visibility visibility service
     */
    public NpcTickManager(Plugin plugin, NpcRegistry registry, VisibilityService visibility) {
        this.plugin = plugin;
        this.registry = registry;
        this.visibility = visibility;
    }

    /** Starts the central tick task (idempotent). */
    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Stops the central tick task. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        tickCounter++;
        int shard = tickCounter % VISIBILITY_INTERVAL;
        var players = Bukkit.getOnlinePlayers();

        for (var npc : registry.all()) {
            // 1) Trait ticks — every tick, isolated against trait bugs.
            for (Trait trait : NpcAccess.traits(npc)) {
                try {
                    trait.onTick();
                } catch (Exception exception) {
                    plugin.getLogger().log(Level.WARNING,
                            "Trait " + trait.getClass().getSimpleName()
                                    + " of NPC '" + npc.getName() + "' threw during onTick", exception);
                }
            }

            // 2) Visibility — sharded by entity id over the interval.
            if (Math.floorMod(npc.getEntityId(), VISIBILITY_INTERVAL) == shard) {
                for (Player player : players) {
                    visibility.update(npc, player);
                }
            }
        }
    }
}
