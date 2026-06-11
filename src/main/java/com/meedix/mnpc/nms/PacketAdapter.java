package com.meedix.mnpc.nms;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.NpcAnimation;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

/**
 * The complete (and only) NMS surface of MNPC.
 *
 * <p>Everything version-specific is hidden behind this interface; the rest of
 * the plugin is written purely against the Bukkit/Paper API. Porting MNPC to
 * a new Minecraft version means implementing this interface once in a new
 * {@code nms/v<version>} package.</p>
 */
public interface PacketAdapter {

    /** @return a fresh, server-unique entity id for packet use. */
    int nextEntityId();

    /**
     * Spawns the NPC for a single viewer: player-info add, entity add,
     * skin-layer metadata, head rotation and equipment.
     *
     * @param viewer the receiving player
     * @param npc    the NPC to show
     */
    void spawnNpc(Player viewer, Npc npc);

    /**
     * Removes the NPC's tab-list entry while keeping the entity visible.
     * Should be called a few ticks after {@link #spawnNpc(Player, Npc)} so the
     * client has loaded the skin.
     *
     * @param viewers the receiving players
     * @param npc     the NPC whose tab entry is removed
     */
    void removeFromTabList(Collection<Player> viewers, Npc npc);

    /**
     * Despawns the NPC for a single viewer (entity remove + tab cleanup).
     *
     * @param viewer the receiving player
     * @param npc    the NPC to hide
     */
    void despawnNpc(Player viewer, Npc npc);

    /**
     * Synchronises the NPC's exact position and rotation with the viewers.
     *
     * @param viewers the receiving players
     * @param npc     the NPC (its current location is sent)
     */
    void sendTeleport(Collection<Player> viewers, Npc npc);

    /**
     * Rotates head and body to an absolute rotation.
     *
     * @param viewers the receiving players
     * @param npc     the NPC to rotate
     * @param yaw     absolute yaw in degrees
     * @param pitch   absolute pitch in degrees
     */
    void sendRotation(Collection<Player> viewers, Npc npc, float yaw, float pitch);

    /**
     * Plays a one-shot animation.
     *
     * @param viewers   the receiving players
     * @param npc       the animated NPC
     * @param animation the animation to play
     */
    void sendAnimation(Collection<Player> viewers, Npc npc, NpcAnimation animation);

    /**
     * Sends the NPC's full equipment state.
     *
     * @param viewers the receiving players
     * @param npc     the NPC whose equipment is sent
     */
    void sendEquipment(Collection<Player> viewers, Npc npc);

    /**
     * Shows or hides the NPC's name tag for the given viewers using a
     * client-side scoreboard team whose name-tag visibility is set to
     * {@code never}. Sending only packets keeps the real server scoreboard
     * untouched.
     *
     * @param viewers the players to update
     * @param npc     the NPC whose name tag changes
     * @param visible {@code true} to show the name tag, {@code false} to hide it
     */
    void sendNameTagVisibility(Collection<Player> viewers, Npc npc, boolean visible);

    /**
     * Spawns a client-side text display (used for holograms).
     *
     * @param viewer   the receiving player
     * @param entityId packet entity id of the display
     * @param uuid     uuid of the display
     * @param location position of the display
     * @param text     the (possibly multi-line) text
     */
    void spawnTextDisplay(Player viewer, int entityId, UUID uuid, Location location, Component text);

    /**
     * Updates the text of a previously spawned text display.
     *
     * @param viewers  the receiving players
     * @param entityId packet entity id of the display
     * @param text     the new text
     */
    void updateTextDisplay(Collection<Player> viewers, int entityId, Component text);

    /**
     * Moves an arbitrary client-side entity (used for hologram follow).
     *
     * @param viewers  the receiving players
     * @param entityId packet entity id
     * @param location the new position
     */
    void teleportEntity(Collection<Player> viewers, int entityId, Location location);

    /**
     * Destroys an arbitrary client-side entity.
     *
     * @param viewers  the receiving players
     * @param entityId packet entity id
     */
    void removeEntity(Collection<Player> viewers, int entityId);
}
