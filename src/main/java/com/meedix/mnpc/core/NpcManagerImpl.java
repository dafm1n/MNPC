package com.meedix.mnpc.core;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.NpcManager;
import com.meedix.mnpc.api.skin.Skin;
import com.meedix.mnpc.core.visibility.VisibilityService;
import com.meedix.mnpc.nms.PacketAdapter;
import com.meedix.mnpc.skin.SkinService;
import com.meedix.mnpc.storage.NpcStorage;
import com.meedix.mnpc.trait.HologramTrait;
import org.bukkit.Location;

import java.util.Collection;
import java.util.List;
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
        addVanillaHologram(npc);
        return npc;
    }

    private void addVanillaHologram(Npc npc) {
        if (!npc.getName().equalsIgnoreCase("Vanilla")) {
            return;
        }
        npc.addTrait(new HologramTrait(List.of(
                "&f &f &f &f &#D3FFA9ᴠ&#D0FFA5ᴀ&#CCFFA1ɴ&#C9FF9Dɪ&#C5FF99ʟ&#C2FF95ʟ&#BEFF91ᴀ &#61E25F⏻ &f &f &f &f",
                "&#FFE4E4&m        ",
                "&#E6FFC6Версия: &#CEFF8F26.1.2",
                "&#E6FFC6     Онлайн: &#CEFF8F%bungee_vanilla%/150     ",
                "&#FCD05C→ жми ←",
                "&f")));
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
