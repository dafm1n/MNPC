package com.meedix.mnpc.trait;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.trait.Trait;
import com.meedix.mnpc.nms.PacketAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * Renders text lines above the NPC's head using a client-side
 * {@code TextDisplay} entity (billboarded, multi-line, no armor stands).
 */
public final class HologramTrait extends Trait {

    /** Vertical offset of the hologram above the NPC's feet, in blocks. */
    private static final double HEIGHT_OFFSET = 2.1;

    private final PacketAdapter adapter;
    private final int displayEntityId;
    private final UUID displayUuid = UUID.randomUUID();

    private List<String> lines;
    private Location lastLocation;
    /** Cached rendered component; rebuilt only when {@link #lines} change. */
    private Component renderedText;

    /**
     * Convenience constructor resolving the packet adapter from the running
     * MNPC plugin instance.
     *
     * @param lines hologram lines, legacy color codes ({@code §}/{@code &}) supported
     */
    public HologramTrait(List<String> lines) {
        this(com.meedix.mnpc.MnpcPlugin.getInstance().getPacketAdapter(), lines);
    }

    /**
     * @param adapter the packet adapter (inject via {@code MnpcPlugin#getPacketAdapter()})
     * @param lines   hologram lines, legacy color codes ({@code §}/{@code &}) supported
     */
    public HologramTrait(PacketAdapter adapter, List<String> lines) {
        this.adapter = adapter;
        this.displayEntityId = adapter.nextEntityId();
        this.lines = List.copyOf(lines);
    }

    /** @return the current hologram lines. */
    public List<String> getLines() {
        return lines;
    }

    /**
     * Replaces the hologram text for all viewers. No packets are sent when
     * the new lines equal the current ones.
     *
     * @param lines the new lines
     */
    public void setLines(List<String> lines) {
        List<String> copy = List.copyOf(lines);
        if (copy.equals(this.lines)) {
            return; // no change — skip re-render and packet dispatch
        }
        this.lines = copy;
        this.renderedText = null;
        adapter.updateTextDisplay(getNpc().getViewers(), displayEntityId, renderText());
    }

    @Override
    public void onSpawn(Player viewer) {
        adapter.spawnTextDisplay(viewer, displayEntityId, displayUuid, hologramLocation(), renderText());
    }

    @Override
    public void onDespawn(Player viewer) {
        adapter.removeEntity(List.of(viewer), displayEntityId);
    }

    @Override
    public void onTick() {
        Location current = hologramLocation();
        if (lastLocation == null
                || lastLocation.getWorld() != current.getWorld()
                || lastLocation.distanceSquared(current) > 0.001) {
            lastLocation = current;
            adapter.teleportEntity(getNpc().getViewers(), displayEntityId, current);
        }
    }

    @Override
    public void onRemove() {
        Npc npc = getNpc();
        if (npc != null) {
            adapter.removeEntity(npc.getViewers(), displayEntityId);
        }
    }

    private Location hologramLocation() {
        return getNpc().getLocation().add(0, HEIGHT_OFFSET, 0);
    }

    private Component renderText() {
        Component cached = renderedText;
        if (cached != null) {
            return cached; // legacy deserialization is not free — do it once per change
        }
        Component text = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                text = text.append(Component.newline());
            }
            text = text.append(LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(lines.get(i).replace('§', '&')));
        }
        renderedText = text;
        return text;
    }
}
