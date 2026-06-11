package com.meedix.mnpc.api;

import com.meedix.mnpc.api.skin.Skin;
import com.meedix.mnpc.api.trait.Trait;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A packet-based (client-side) player NPC.
 *
 * <p>An {@code Npc} never exists as a real server entity: it is rendered for
 * nearby players purely through clientbound packets, occupies no tab-list slot
 * (after the initial info packet is retracted) and has near-zero server-side
 * cost. All mutating methods are safe to call from the main thread; packet
 * dispatch itself is thread-safe.</p>
 *
 * @see NpcManager#createNpc(String, Location)
 */
public interface Npc {

    /** @return the unique, immutable identifier of this NPC (used for storage). */
    UUID getId();

    /** @return the per-session entity id used in packets. */
    int getEntityId();

    /** @return the current display/profile name (at most 16 characters). */
    String getName();

    /**
     * Changes the NPC name on the fly. Because the name is part of the
     * {@code GameProfile}, the NPC is transparently respawned for all viewers.
     *
     * @param name the new name, 1-16 characters
     */
    void setName(String name);

    /** @return a clone of the NPC's current location. */
    Location getLocation();

    /**
     * Teleports the NPC. Works across worlds: viewers that can no longer see
     * the NPC are despawned, new viewers are picked up by the visibility tick.
     *
     * @param location the destination
     */
    void teleport(Location location);

    /** @return the current skin, if one is set. */
    Optional<Skin> getSkin();

    /**
     * Applies a skin from raw Mojang texture data and respawns the NPC for
     * all current viewers.
     *
     * @param texture   Base64 texture payload
     * @param signature Base64 Yggdrasil signature of the payload
     */
    void setSkin(String texture, String signature);

    /** @see #setSkin(String, String) */
    void setSkin(Skin skin);

    /**
     * Sets (or clears) an equipment piece.
     *
     * @param slot the slot to change
     * @param item the item, or {@code null}/AIR to clear the slot
     */
    void setEquipment(NpcEquipmentSlot slot, ItemStack item);

    /** @return an immutable snapshot of the current equipment. */
    Map<NpcEquipmentSlot, ItemStack> getEquipment();

    /**
     * Rotates the NPC's head and body to the given absolute rotation
     * for all viewers.
     *
     * @param yaw   absolute yaw in degrees
     * @param pitch absolute pitch in degrees
     */
    void setRotation(float yaw, float pitch);

    /**
     * Rotates the NPC so it faces the given player (for all viewers).
     *
     * @param player the player to face
     */
    void lookAt(Player player);

    /**
     * Rotates the NPC towards a player, visible only to that player.
     * Used by per-viewer behaviours such as {@code LookAtPlayerTrait}.
     *
     * @param viewer the only player that receives the rotation packets
     */
    void lookAtFor(Player viewer);

    /**
     * Plays a one-shot animation for all viewers.
     *
     * @param animation the animation to play
     */
    void playAnimation(NpcAnimation animation);

    /**
     * Attaches a trait. The trait immediately receives {@code onSpawn()} for
     * every player currently seeing the NPC.
     *
     * @param trait the trait instance (one instance per NPC)
     * @throws IllegalStateException if a trait of the same class is already attached
     */
    void addTrait(Trait trait);

    /**
     * Detaches a trait, invoking {@code onRemove()}.
     *
     * @param type the trait class
     * @return {@code true} if a trait was removed
     */
    boolean removeTrait(Class<? extends Trait> type);

    /** @return the attached trait of the given type, if present. */
    <T extends Trait> Optional<T> getTrait(Class<T> type);

    /** @return all attached traits. */
    Collection<Trait> getTraits();

    /** @return the players that currently see this NPC. */
    Collection<Player> getViewers();

    /** @return whether the given player currently sees this NPC. */
    boolean isVisibleTo(Player player);

    /**
     * @return the radius (in blocks) within which players automatically
     *         see this NPC
     */
    double getViewRadius();

    /** @param radius the new automatic visibility radius in blocks */
    void setViewRadius(double radius);

    /** @return whether the name tag above the NPC's head is visible. */
    boolean isNameVisible();

    /**
     * Shows or hides the name tag rendered above the NPC's head. Hiding is
     * implemented with a client-side scoreboard team, so no server-side
     * scoreboard state is touched. The change applies instantly to all
     * current viewers and is persisted to storage.
     *
     * @param visible {@code true} to show the name tag, {@code false} to hide it
     */
    void setNameVisible(boolean visible);

    /** @return whether the NPC has been removed from its manager. */
    boolean isRemoved();
}
