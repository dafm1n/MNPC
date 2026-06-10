package com.meedix.mnpc.api.trait;

import com.meedix.mnpc.api.Npc;
import org.bukkit.entity.Player;

/**
 * A pluggable behaviour attached to a single NPC.
 *
 * <p>Traits are ticked by the central {@code NpcTickManager} — there is never
 * a per-NPC scheduler task. Implementations must therefore keep
 * {@link #onTick()} cheap; expensive work should be staggered or off-loaded.</p>
 *
 * <pre>{@code
 * npc.addTrait(new LookAtPlayerTrait(8.0));
 * npc.addTrait(new HologramTrait(List.of("§6Shop", "§7right click")));
 * }</pre>
 */
public abstract class Trait {

    private Npc npc;

    /**
     * Internal: binds this trait to its NPC. Called exactly once by the NPC
     * when the trait is attached.
     *
     * @param npc the owning NPC
     * @throws IllegalStateException if the trait is already attached
     */
    public final void attach(Npc npc) {
        if (this.npc != null) {
            throw new IllegalStateException("Trait " + getClass().getSimpleName() + " is already attached");
        }
        this.npc = npc;
    }

    /** @return the NPC this trait is attached to, or {@code null} before attachment. */
    protected final Npc getNpc() {
        return npc;
    }

    /**
     * Called whenever the NPC is spawned (shown) for a player.
     *
     * @param viewer the player who now sees the NPC
     */
    public void onSpawn(Player viewer) {
    }

    /**
     * Called whenever the NPC is despawned (hidden) for a player, including
     * on player quit and world change.
     *
     * @param viewer the player who no longer sees the NPC
     */
    public void onDespawn(Player viewer) {
    }

    /**
     * Called every server tick by the central tick manager while the NPC is
     * registered. Must be cheap.
     */
    public void onTick() {
    }

    /**
     * Called when the trait is detached or the NPC is removed. Release any
     * per-viewer state here.
     */
    public void onRemove() {
    }
}
