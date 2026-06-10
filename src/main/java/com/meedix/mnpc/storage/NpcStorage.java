package com.meedix.mnpc.storage;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.core.NpcManagerImpl;

import java.util.Collection;

/**
 * Persistence backend for NPCs. The default implementation is
 * {@link YamlNpcStorage}; alternative backends (SQL, JSON, ...) only need to
 * implement this interface.
 */
public interface NpcStorage {

    /**
     * Loads all persisted NPCs into the given manager.
     *
     * @param manager the manager that restores the NPCs
     * @return the number of NPCs loaded
     */
    int load(NpcManagerImpl manager);

    /**
     * Persists the given NPCs, replacing the previous state.
     *
     * @param npcs all NPCs to persist
     */
    void save(Collection<Npc> npcs);
}
