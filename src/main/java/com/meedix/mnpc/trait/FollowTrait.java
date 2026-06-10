package com.meedix.mnpc.trait;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.trait.Trait;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Makes the NPC walk towards a target player (straight-line steering, no
 * pathfinding — intended for lobby companions and escort effects).
 */
public final class FollowTrait extends Trait {

    /** Distance at which the NPC stops approaching its target. */
    private static final double STOP_DISTANCE = 2.5;
    /** Distance at which the NPC snaps to the target instead of walking. */
    private static final double SNAP_DISTANCE = 24.0;
    /** Movement speed in blocks per tick. */
    private static final double SPEED = 0.22;

    private Player target;

    /** Creates the trait without an initial target. */
    public FollowTrait() {
    }

    /**
     * @param target the player to follow, or {@code null} to stop following
     */
    public void setTarget(Player target) {
        this.target = target;
    }

    /** @return the current follow target, or {@code null}. */
    public Player getTarget() {
        return target;
    }

    @Override
    public void onTick() {
        Player currentTarget = target;
        if (currentTarget == null || !currentTarget.isOnline()) {
            return;
        }
        Npc npc = getNpc();
        Location npcLocation = npc.getLocation();
        Location targetLocation = currentTarget.getLocation();
        if (!npcLocation.getWorld().equals(targetLocation.getWorld())) {
            npc.teleport(targetLocation);
            return;
        }

        double distanceSquared = npcLocation.distanceSquared(targetLocation);
        if (distanceSquared <= STOP_DISTANCE * STOP_DISTANCE) {
            npc.lookAt(currentTarget);
            return;
        }
        if (distanceSquared >= SNAP_DISTANCE * SNAP_DISTANCE) {
            npc.teleport(targetLocation);
            return;
        }

        Vector direction = targetLocation.toVector().subtract(npcLocation.toVector()).normalize();
        Location step = npcLocation.add(direction.multiply(SPEED));
        step.setYaw(yawTowards(direction));
        step.setPitch(0.0F);
        npc.teleport(step);
    }

    @Override
    public void onRemove() {
        target = null;
    }

    private static float yawTowards(Vector direction) {
        return (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }
}
