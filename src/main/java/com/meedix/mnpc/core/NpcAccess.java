package com.meedix.mnpc.core;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.trait.Trait;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

/**
 * Engine-internal bridge exposing the live mutable state of an
 * {@link NpcImpl} to the other engine packages (visibility, ticking).
 *
 * <p><strong>Not part of the public API</strong> — plugin consumers must use
 * the read-only views on {@link Npc} instead.</p>
 */
public final class NpcAccess {

    private NpcAccess() {
    }

    /** @return the live viewer set of the NPC. */
    public static Set<Player> viewers(Npc npc) {
        return ((NpcImpl) npc).viewerSet();
    }

    /** @return the live trait list of the NPC. */
    public static List<Trait> traits(Npc npc) {
        return ((NpcImpl) npc).traitList();
    }
}
