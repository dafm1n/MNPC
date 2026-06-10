package com.meedix.mnpc.core;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.NpcManager;
import com.meedix.mnpc.api.skin.Skin;
import com.meedix.mnpc.core.visibility.VisibilityService;
import com.meedix.mnpc.nms.PacketAdapter;
import com.meedix.mnpc.skin.SkinService;
import com.meedix.mnpc.storage.NpcStorage;
import org.bukkit.Location;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Default {@link NpcManager} implementation: creates, indexes, removes and
 * persists NPCs.
 */
public final class NpcManagerImpl implements NpcManager {

    /** Default automatic visibility radius in blocks. */
    private final double defaultViewRadius;

    private final NpcRegistry registry;
    private final VisibilityService visibility;
    private final PacketAdapter adapter;
    private final SkinService skinService;
    private final NpcStorage storage;

    /**
     * @param registry          NPC registry
     * @param visibility        visibility service
     * @param adapter           packet adapter
     * @param skinService       async Mojang skin resolver
     * @param storage           persistence backend
     * @param defaultViewRadius default view radius for new NPCs
     */
    public NpcManagerImpl(NpcRegistry registry, VisibilityService visibility, PacketAdapter adapter,
                          SkinService skinService, NpcStorage storage, double defaultViewRadius) {
        this.registry = registry;
        this.visibility = visibility;
        this.adapter = adapter;
        this.skinService = skinService;
        this.storage = storage;
        this.defaultViewRadius = defaultViewRadius;
    }

    @Override
    public Npc createNpc(String name, Location location) {
        return createNpc(name, location, null);
    }

    @Override
    public Npc createNpc(String name, Location location, Skin skin) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(location.getWorld(), "location world");
        NpcImpl npc = new NpcImpl(UUID.randomUUID(), name, location, skin,
                defaultViewRadius, adapter, visibility);
        registry.register(npc);
        return npc;
    }

    /**
     * Restores a persisted NPC with a fixed id (used by storage on startup).
     *
     * @return the restored NPC
     */
    public Npc restoreNpc(UUID id, String name, Location location, Skin skin, double viewRadius) {
        NpcImpl npc = new NpcImpl(id, name, location, skin, viewRadius, adapter, visibility);
        registry.register(npc);
        return npc;
    }

    @Override
    public void removeNpc(Npc npc) {
        Objects.requireNonNull(npc, "npc");
        if (!(npc instanceof NpcImpl impl) || impl.isRemoved()) {
            return;
        }
        visibility.hideFromAll(impl);
        impl.markRemoved();
        registry.unregister(impl);
    }

    @Override
    public Optional<Npc> getNpc(UUID id) {
        return registry.byId(id);
    }

    @Override
    public Optional<Npc> getNpc(String name) {
        return registry.byName(name);
    }

    @Override
    public Optional<Npc> getNpcByEntityId(int entityId) {
        return registry.byEntityId(entityId);
    }

    @Override
    public Collection<Npc> getNpcs() {
        return registry.all();
    }

    @Override
    public CompletableFuture<Skin> fetchSkin(String playerName) {
        return skinService.fetchByName(playerName);
    }

    @Override
    public void saveAll() {
        storage.save(registry.all());
    }
}
