package com.meedix.mnpc.core;

import com.meedix.mnpc.api.Npc;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of all live NPCs, indexed by storage id and by packet
 * entity id (the latter enables O(1) interaction lookups).
 */
public final class NpcRegistry {

    private final Map<UUID, NpcImpl> byId = new ConcurrentHashMap<>();
    private final Map<Integer, NpcImpl> byEntityId = new ConcurrentHashMap<>();

    /** Registers a newly created NPC. */
    void register(NpcImpl npc) {
        byId.put(npc.getId(), npc);
        byEntityId.put(npc.getEntityId(), npc);
    }

    /** Unregisters a removed NPC. */
    void unregister(NpcImpl npc) {
        byId.remove(npc.getId());
        byEntityId.remove(npc.getEntityId());
    }

    /** @return the NPC with the given storage id. */
    public Optional<Npc> byId(UUID id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** @return the NPC with the given packet entity id. */
    public Optional<Npc> byEntityId(int entityId) {
        return Optional.ofNullable(byEntityId.get(entityId));
    }

    /** @return the first NPC with the given (case-insensitive) name. */
    public Optional<Npc> byName(String name) {
        for (NpcImpl npc : byId.values()) {
            if (npc.getName().equalsIgnoreCase(name)) {
                return Optional.of(npc);
            }
        }
        return Optional.empty();
    }

    /** @return an immutable snapshot of all NPCs. */
    public Collection<Npc> all() {
        return List.copyOf(byId.values());
    }

    /** @return the live internal collection (engine internal, do not mutate). */
    Collection<NpcImpl> allInternal() {
        return byId.values();
    }
}
