package com.meedix.mnpc.nms.v26_2;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.NpcAnimation;
import com.meedix.mnpc.api.NpcEquipmentSlot;
import com.meedix.mnpc.api.skin.Skin;
import com.mojang.authlib.GameProfile;
import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import io.netty.buffer.Unpooled;
import io.papermc.paper.adventure.PaperAdventure;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.meedix.mnpc.nms.PacketAdapter;

/**
 * {@link PacketAdapter} implementation for Minecraft 26.2 (Mojang mappings,
 * Paper). All packets are written directly to the player's
 * {@link net.minecraft.network.Connection} obtained from
 * {@link net.minecraft.server.network.ServerGamePacketListenerImpl}.
 *
 * <p>Implementation notes:</p>
 * <ul>
 *   <li>NPCs are added to the player info map with {@code listed = false},
 *       so they never occupy a tab-list slot while their skin still loads.</li>
 *   <li>No fake {@code ServerPlayer} instances are created — every packet is
 *       built from raw values, keeping memory cost per NPC minimal.</li>
 *   <li>{@code ClientboundRotateHeadPacket} exposes no raw-value constructor,
 *       so it is built through its {@code STREAM_CODEC} (varint entity id +
 *       angle byte) instead of reflection.</li>
 * </ul>
 */
public final class PacketAdapterImpl implements PacketAdapter {

    /**
     * SynchedEntityData index of the player "displayed skin parts" byte.
     * Resolved from the public {@link net.minecraft.world.entity.Avatar}
     * accessor, so it survives index shifts between versions.
     */
    private static final int DATA_PLAYER_SKIN_PARTS =
            net.minecraft.world.entity.Avatar.DATA_PLAYER_MODE_CUSTOMISATION.id();
    /** All skin layers enabled (cape, jacket, sleeves, pants, hat). */
    private static final byte SKIN_PARTS_ALL = 0x7F;
    /** SynchedEntityData index of the display entity billboard byte (26.2, verified). */
    private static final int DATA_DISPLAY_BILLBOARD = 15;
    /** Billboard constraint CENTER: the display always faces the viewer. */
    private static final byte BILLBOARD_CENTER = 3;
    /** SynchedEntityData index of the text display text component (26.2, verified). */
    private static final int DATA_TEXT_DISPLAY_TEXT = 23;

    /** ClientboundAnimatePacket action: swing main arm. */
    private static final int ANIM_SWING_MAIN_ARM = 0;
    /** ClientboundAnimatePacket action: swing off hand. */
    private static final int ANIM_SWING_OFF_HAND = 3;
    /** ClientboundAnimatePacket action: critical hit particles. */
    private static final int ANIM_CRITICAL_HIT = 4;
    /** ClientboundAnimatePacket action: magic critical hit particles. */
    private static final int ANIM_MAGIC_CRITICAL_HIT = 5;

    @Override
    public int nextEntityId() {
        // 26.2: the static Entity.nextEntityId() allocator was removed; the counter now
        // lives on the level (ServerLevel#getNextEntityId). Entity ids are effectively
        // global, so allocating from the first loaded world is sufficient for the
        // client-side packet entities used here.
        return ((org.bukkit.craftbukkit.CraftWorld) org.bukkit.Bukkit.getWorlds().get(0))
                .getHandle().getNextEntityId();
    }

    @Override
    public void spawnNpc(Player viewer, Npc npc) {
        Location location = npc.getLocation();
        GameProfile profile = buildProfile(npc);

        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                npc.getId(), profile,
                false,                      // listed: never show in the tab list
                0,                          // latency
                GameType.SURVIVAL,
                null,                       // tab display name (unused, not listed)
                false,                      // showHat in tab (unused)
                0,                          // tab list order (unused)
                null                        // no chat session
        );
        ClientboundPlayerInfoUpdatePacket infoPacket = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER), List.of(entry));

        ClientboundAddEntityPacket addPacket = new ClientboundAddEntityPacket(
                npc.getEntityId(), npc.getId(),
                location.getX(), location.getY(), location.getZ(),
                location.getPitch(), location.getYaw(),
                EntityTypes.PLAYER, 0, Vec3.ZERO, location.getYaw());

        ClientboundSetEntityDataPacket dataPacket = new ClientboundSetEntityDataPacket(
                npc.getEntityId(),
                List.of(new SynchedEntityData.DataValue<>(
                        DATA_PLAYER_SKIN_PARTS, EntityDataSerializers.BYTE, SKIN_PARTS_ALL)));

        send(viewer, infoPacket, addPacket, dataPacket,
                rotateHeadPacket(npc.getEntityId(), location.getYaw()));
        sendEquipment(List.of(viewer), npc);
        if (!npc.isNameVisible()) {
            sendNameTagVisibility(List.of(viewer), npc, false);
        }
    }

    @Override
    public void removeFromTabList(Collection<Player> viewers, Npc npc) {
        broadcast(viewers, new ClientboundPlayerInfoRemovePacket(List.of(npc.getId())));
    }

    @Override
    public void despawnNpc(Player viewer, Npc npc) {
        send(viewer,
                new ClientboundRemoveEntitiesPacket(npc.getEntityId()),
                new ClientboundPlayerInfoRemovePacket(List.of(npc.getId())));
        if (!npc.isNameVisible()) {
            // Remove the client-side hide-team so nothing leaks on the client.
            sendNameTagVisibility(List.of(viewer), npc, true);
        }
    }

    @Override
    public void sendNameTagVisibility(Collection<Player> viewers, Npc npc, boolean visible) {
        // A throwaway scoreboard scopes the team to these packets only; the
        // real server scoreboard is never touched.
        PlayerTeam team = new PlayerTeam(new Scoreboard(), "mnpc_" + npc.getEntityId());
        Packet<?> packet;
        if (visible) {
            packet = ClientboundSetPlayerTeamPacket.createRemovePacket(team);
        } else {
            team.setNameTagVisibility(Team.Visibility.NEVER);
            team.getPlayers().add(npc.getName());
            packet = ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true);
        }
        for (Player viewer : viewers) {
            send(viewer, packet);
        }
    }

    @Override
    public void sendTeleport(Collection<Player> viewers, Npc npc) {
        Location location = npc.getLocation();
        ClientboundEntityPositionSyncPacket syncPacket = new ClientboundEntityPositionSyncPacket(
                npc.getEntityId(),
                new PositionMoveRotation(
                        new Vec3(location.getX(), location.getY(), location.getZ()),
                        Vec3.ZERO, location.getYaw(), location.getPitch()),
                true);
        broadcast(viewers, syncPacket);
        broadcast(viewers, rotateHeadPacket(npc.getEntityId(), location.getYaw()));
    }

    @Override
    public void sendRotation(Collection<Player> viewers, Npc npc, float yaw, float pitch) {
        byte yawByte = toAngleByte(yaw);
        byte pitchByte = toAngleByte(pitch);
        broadcast(viewers, new ClientboundMoveEntityPacket.Rot(npc.getEntityId(), yawByte, pitchByte, true));
        broadcast(viewers, rotateHeadPacket(npc.getEntityId(), yaw));
    }

    @Override
    public void sendAnimation(Collection<Player> viewers, Npc npc, NpcAnimation animation) {
        Packet<?> packet = switch (animation) {
            case SWING_MAIN_ARM -> animatePacket(npc.getEntityId(), ANIM_SWING_MAIN_ARM);
            case SWING_OFF_HAND -> animatePacket(npc.getEntityId(), ANIM_SWING_OFF_HAND);
            case CRITICAL_HIT -> animatePacket(npc.getEntityId(), ANIM_CRITICAL_HIT);
            case MAGIC_CRITICAL_HIT -> animatePacket(npc.getEntityId(), ANIM_MAGIC_CRITICAL_HIT);
            case HURT -> new ClientboundHurtAnimationPacket(npc.getEntityId(), 0.0F);
        };
        broadcast(viewers, packet);
    }

    @Override
    public void sendEquipment(Collection<Player> viewers, Npc npc) {
        Map<NpcEquipmentSlot, ItemStack> equipment = npc.getEquipment();
        List<com.mojang.datafixers.util.Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> slots =
                new ArrayList<>(NpcEquipmentSlot.values().length);
        for (NpcEquipmentSlot slot : NpcEquipmentSlot.values()) {
            ItemStack item = equipment.get(slot);
            net.minecraft.world.item.ItemStack nmsItem = item == null
                    ? net.minecraft.world.item.ItemStack.EMPTY
                    : CraftItemStack.asNMSCopy(item);
            slots.add(com.mojang.datafixers.util.Pair.of(toNmsSlot(slot), nmsItem));
        }
        broadcast(viewers, new ClientboundSetEquipmentPacket(npc.getEntityId(), slots));
    }

    @Override
    public void spawnTextDisplay(Player viewer, int entityId, UUID uuid,
                                 Location location, net.kyori.adventure.text.Component text) {
        ClientboundAddEntityPacket addPacket = new ClientboundAddEntityPacket(
                entityId, uuid,
                location.getX(), location.getY(), location.getZ(),
                0.0F, 0.0F, EntityTypes.TEXT_DISPLAY, 0, Vec3.ZERO, 0.0);
        send(viewer, addPacket, textDisplayDataPacket(entityId, text));
    }

    @Override
    public void updateTextDisplay(Collection<Player> viewers, int entityId,
                                  net.kyori.adventure.text.Component text) {
        broadcast(viewers, textDisplayDataPacket(entityId, text));
    }

    @Override
    public void teleportEntity(Collection<Player> viewers, int entityId, Location location) {
        broadcast(viewers, new ClientboundEntityPositionSyncPacket(
                entityId,
                new PositionMoveRotation(
                        new Vec3(location.getX(), location.getY(), location.getZ()),
                        Vec3.ZERO, 0.0F, 0.0F),
                true));
    }

    @Override
    public void removeEntity(Collection<Player> viewers, int entityId) {
        broadcast(viewers, new ClientboundRemoveEntitiesPacket(entityId));
    }

    /**
     * Builds the NPC's {@link GameProfile} including its skin texture
     * property when present.
     */
    private GameProfile buildProfile(Npc npc) {
        Skin skin = npc.getSkin().orElse(null);
        if (skin == null) {
            return new GameProfile(npc.getId(), npc.getName());
        }
        // GameProfile is a record in authlib 7.x: properties are supplied
        // through the constructor instead of being mutated afterwards.
        PropertyMap properties = new PropertyMap(ImmutableMultimap.of("textures",
                new Property("textures", skin.texture(), skin.signature())));
        return new GameProfile(npc.getId(), npc.getName(), properties);
    }

    /** Builds the entity-data packet carrying billboard mode and text. */
    private ClientboundSetEntityDataPacket textDisplayDataPacket(int entityId,
                                                                 net.kyori.adventure.text.Component text) {
        return new ClientboundSetEntityDataPacket(entityId, List.of(
                new SynchedEntityData.DataValue<>(
                        DATA_DISPLAY_BILLBOARD, EntityDataSerializers.BYTE, BILLBOARD_CENTER),
                new SynchedEntityData.DataValue<>(
                        DATA_TEXT_DISPLAY_TEXT, EntityDataSerializers.COMPONENT,
                        PaperAdventure.asVanilla(text))));
    }

    /**
     * Builds a {@link ClientboundRotateHeadPacket} from raw values via its
     * stream codec (the packet has no public raw-value constructor and we
     * never keep server-side entity instances).
     */
    private ClientboundRotateHeadPacket rotateHeadPacket(int entityId, float yaw) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(6));
        try {
            buf.writeVarInt(entityId);
            buf.writeByte(toAngleByte(yaw));
            return ClientboundRotateHeadPacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    private ClientboundAnimatePacket animatePacket(int entityId, int action) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(6));
        try {
            buf.writeVarInt(entityId);
            buf.writeByte(action);
            return ClientboundAnimatePacket.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    /** Converts degrees to the protocol's 1/256-turn angle byte. */
    private static byte toAngleByte(float degrees) {
        return (byte) Math.floor(degrees * 256.0F / 360.0F);
    }

    private static EquipmentSlot toNmsSlot(NpcEquipmentSlot slot) {
        return switch (slot) {
            case HELMET -> EquipmentSlot.HEAD;
            case CHESTPLATE -> EquipmentSlot.CHEST;
            case LEGGINGS -> EquipmentSlot.LEGS;
            case BOOTS -> EquipmentSlot.FEET;
            case MAIN_HAND -> EquipmentSlot.MAINHAND;
            case OFF_HAND -> EquipmentSlot.OFFHAND;
        };
    }

    /** Writes packets directly to the viewer's {@link net.minecraft.network.Connection}. */
    private static void send(Player viewer, Packet<?>... packets) {
        var connection = ((CraftPlayer) viewer).getHandle().connection.connection;
        for (Packet<?> packet : packets) {
            connection.send(packet);
        }
    }

    private static void broadcast(Collection<Player> viewers, Packet<?> packet) {
        for (Player viewer : viewers) {
            ((CraftPlayer) viewer).getHandle().connection.connection.send(packet);
        }
    }
}
