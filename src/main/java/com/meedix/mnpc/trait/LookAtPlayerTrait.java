package com.meedix.mnpc.trait;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.trait.Trait;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Makes the NPC look at the nearest viewer. Rotation packets are sent
 * per-viewer, so every player sees the NPC tracking <em>them</em> while they
 * are the closest viewer in range.
 */
public final class LookAtPlayerTrait extends Trait {

    /** Default tracking range in blocks. */
    public static final double DEFAULT_RANGE = 8.0;
    /** Rotations are sent at most every this many ticks. */
    private static final int UPDATE_INTERVAL_TICKS = 2;

    private final double range;
    private int tick;

    /** Creates the trait with {@link #DEFAULT_RANGE}. */
    public LookAtPlayerTrait() {
        this(DEFAULT_RANGE);
    }

    /**
     * @param range tracking range in blocks
     */
    public LookAtPlayerTrait(double range) {
        if (range <= 0) {
            throw new IllegalArgumentException("Range must be positive: " + range);
        }
        this.range = range;
    }

    /** @return the tracking range in blocks. */
    public double getRange() {
        return range;
    }

    @Override
    public void onTick() {
        if (++tick % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }
        Npc npc = getNpc();
        Location npcLocation = npc.getLocation();
        double rangeSquared = range * range;
        for (Player viewer : npc.getViewers()) {
            if (viewer.getWorld().equals(npcLocation.getWorld())
                    && viewer.getLocation().distanceSquared(npcLocation) <= rangeSquared) {
                npc.lookAtFor(viewer);
            }
        }
    }
}
