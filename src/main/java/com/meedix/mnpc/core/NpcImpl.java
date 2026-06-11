package com.meedix.mnpc.core;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.NpcAnimation;
import com.meedix.mnpc.api.NpcEquipmentSlot;
import com.meedix.mnpc.api.skin.Skin;
import com.meedix.mnpc.api.trait.Trait;
import com.meedix.mnpc.core.visibility.VisibilityService;
import com.meedix.mnpc.nms.PacketAdapter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default {@link Npc} implementation. Holds pure state plus the packet
 * adapter; all visibility decisions live in {@link VisibilityService}.
 */
public final class NpcImpl implements Npc {

    private static final int MAX_NAME_LENGTH = 16;

    private final UUID id;
    private final int entityId;
    private final PacketAdapter adapter;
    private final VisibilityService visibility;

    private volatile String name;
    private volatile Location location;
    private volatile Skin skin;
    private volatile double viewRadius;
    private volatile boolean nameVisible = true;
    private volatile boolean removed;

    private final Map<NpcEquipmentSlot, ItemStack> equipment = new EnumMap<>(NpcEquipmentSlot.class);
    private final List<Trait> traits = new CopyOnWriteArrayList<>();
    private final Set<Player> viewers = ConcurrentHashMap.newKeySet();

    /**
     * @param id         storage id
     * @param name       profile name (1-16 chars)
     * @param location   initial location
     * @param skin       initial skin, may be null
     * @param viewRadius automatic visibility radius in blocks
     * @param adapter    version adapter used for packet dispatch
     * @param visibility visibility service notified about state changes
     */
    NpcImpl(UUID id, String name, Location location, Skin skin, double viewRadius,
            PacketAdapter adapter, VisibilityService visibility) {
        this.id = id;
        this.entityId = adapter.nextEntityId();
        this.name = validateName(name);
        this.location = location.clone();
        this.skin = skin;
        this.viewRadius = viewRadius;
        this.adapter = adapter;
        this.visibility = visibility;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public int getEntityId() {
        return entityId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = validateName(name);
        respawnForViewers();
    }

    @Override
    public Location getLocation() {
        return location.clone();
    }

    @Override
    public void teleport(Location destination) {
        Objects.requireNonNull(destination.getWorld(), "destination world");
        boolean changedWorld = !destination.getWorld().equals(location.getWorld());
        this.location = destination.clone();
        if (changedWorld) {
            // Old-world viewers can no longer see the NPC; the visibility
            // tick will pick up players in the new world automatically.
            for (Player viewer : List.copyOf(viewers)) {
                visibility.hide(this, viewer);
            }
        } else {
            adapter.sendTeleport(viewers, this);
        }
    }

    @Override
    public Optional<Skin> getSkin() {
        return Optional.ofNullable(skin);
    }

    @Override
    public void setSkin(String texture, String signature) {
        setSkin(new Skin(texture, signature));
    }

    @Override
    public void setSkin(Skin skin) {
        this.skin = Objects.requireNonNull(skin, "skin");
        respawnForViewers();
    }

    @Override
    public void setEquipment(NpcEquipmentSlot slot, ItemStack item) {
        Objects.requireNonNull(slot, "slot");
        synchronized (equipment) {
            if (item == null || item.getType().isAir()) {
                equipment.remove(slot);
            } else {
                equipment.put(slot, item.clone());
            }
        }
        adapter.sendEquipment(viewers, this);
    }

    @Override
    public Map<NpcEquipmentSlot, ItemStack> getEquipment() {
        synchronized (equipment) {
            return Map.copyOf(equipment);
        }
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        location.setYaw(yaw);
        location.setPitch(pitch);
        adapter.sendRotation(viewers, this, yaw, pitch);
    }

    @Override
    public void lookAt(Player player) {
        float[] rotation = rotationTowards(player);
        setRotation(rotation[0], rotation[1]);
    }

    @Override
    public void lookAtFor(Player viewer) {
        if (!viewers.contains(viewer)) {
            return;
        }
        float[] rotation = rotationTowards(viewer);
        adapter.sendRotation(List.of(viewer), this, rotation[0], rotation[1]);
    }

    @Override
    public void playAnimation(NpcAnimation animation) {
        adapter.sendAnimation(viewers, this, Objects.requireNonNull(animation, "animation"));
    }

    @Override
    public void addTrait(Trait trait) {
        Objects.requireNonNull(trait, "trait");
        if (traits.stream().anyMatch(existing -> existing.getClass() == trait.getClass())) {
            throw new IllegalStateException(
                    "Trait " + trait.getClass().getSimpleName() + " is already attached to " + name);
        }
        trait.attach(this);
        traits.add(trait);
        for (Player viewer : viewers) {
            trait.onSpawn(viewer);
        }
    }

    @Override
    public boolean removeTrait(Class<? extends Trait> type) {
        for (Trait trait : traits) {
            if (type.isInstance(trait)) {
                traits.remove(trait);
                trait.onRemove();
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Trait> Optional<T> getTrait(Class<T> type) {
        for (Trait trait : traits) {
            if (type.isInstance(trait)) {
                return Optional.of((T) trait);
            }
        }
        return Optional.empty();
    }

    @Override
    public Collection<Trait> getTraits() {
        return List.copyOf(traits);
    }

    @Override
    public Collection<Player> getViewers() {
        return List.copyOf(viewers);
    }

    @Override
    public boolean isVisibleTo(Player player) {
        return viewers.contains(player);
    }

    @Override
    public double getViewRadius() {
        return viewRadius;
    }

    @Override
    public void setViewRadius(double radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("View radius must be positive: " + radius);
        }
        this.viewRadius = radius;
    }

    @Override
    public boolean isRemoved() {
        return removed;
    }

    // ------------------------------------------------------------------
    // Internal accessors used by the engine (package-private by design).
    // ------------------------------------------------------------------

    /** @return the live, mutable viewer set (engine internal). */
    Set<Player> viewerSet() {
        return viewers;
    }

    /** @return the live trait list (engine internal). */
    List<Trait> traitList() {
        return traits;
    }

    @Override
    public boolean isNameVisible() {
        return nameVisible;
    }

    @Override
    public void setNameVisible(boolean visible) {
        if (this.nameVisible == visible) {
            return;
        }
        this.nameVisible = visible;
        if (!viewers.isEmpty()) {
            adapter.sendNameTagVisibility(List.copyOf(viewers), this, visible);
        }
    }

    /** Marks this NPC as removed and detaches all traits. */
    void markRemoved() {
        removed = true;
        for (Trait trait : traits) {
            trait.onRemove();
        }
        traits.clear();
    }

    /** Despawns and respawns the NPC for all current viewers (profile changes). */
    private void respawnForViewers() {
        for (Player viewer : List.copyOf(viewers)) {
            visibility.hide(this, viewer);
            visibility.show(this, viewer);
        }
    }

    /** @return {yaw, pitch} pointing from the NPC's eyes to the player's eyes. */
    private float[] rotationTowards(Player player) {
        Location eye = location.clone().add(0, 1.62, 0);
        Location target = player.getEyeLocation();
        double dx = target.getX() - eye.getX();
        double dy = target.getY() - eye.getY();
        double dz = target.getZ() - eye.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontal));
        return new float[]{yaw, pitch};
    }

    private static String validateName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "NPC name must be 1-" + MAX_NAME_LENGTH + " characters: '" + name + "'");
        }
        return name;
    }
}
