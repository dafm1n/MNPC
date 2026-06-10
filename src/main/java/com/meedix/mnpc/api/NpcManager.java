package com.meedix.mnpc.api;

import com.meedix.mnpc.api.skin.Skin;
import org.bukkit.Location;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Entry point of the MNPC API. Obtain via
 * {@code Bukkit.getServicesManager().load(NpcManager.class)} or
 * {@code MnpcPlugin#getNpcManager()}.
 */
public interface NpcManager {

    /**
     * Creates and registers a new NPC. The NPC is spawned automatically for
     * all players inside its view radius on the next visibility tick.
     *
     * @param name     profile name, 1-16 characters
     * @param location initial location (world must be loaded)
     * @return the created NPC
     * @throws IllegalArgumentException if the name is invalid or the world is null
     */
    Npc createNpc(String name, Location location);

    /**
     * Creates a new NPC with a pre-resolved skin.
     *
     * @see #createNpc(String, Location)
     */
    Npc createNpc(String name, Location location, Skin skin);

    /**
     * Removes an NPC: despawns it for all viewers, removes its traits and
     * deletes it from storage on the next save.
     *
     * @param npc the NPC to remove
     */
    void removeNpc(Npc npc);

    /** @return the NPC with the given storage id, if registered. */
    Optional<Npc> getNpc(UUID id);

    /** @return the first NPC with the given name (names are not unique). */
    Optional<Npc> getNpc(String name);

    /** @return the NPC backing the given packet entity id, if any. */
    Optional<Npc> getNpcByEntityId(int entityId);

    /** @return an immutable snapshot of all registered NPCs. */
    Collection<Npc> getNpcs();

    /**
     * Resolves the skin (texture + signature) of an existing Minecraft
     * account asynchronously via the Mojang API. Results are cached.
     *
     * @param playerName the account name
     * @return a future completing with the skin, or exceptionally if the
     *         account does not exist or the API is unreachable
     */
    CompletableFuture<Skin> fetchSkin(String playerName);

    /** Persists all NPCs to disk immediately (synchronous I/O). */
    void saveAll();
}
