package com.meedix.mnpc.storage;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.NpcEquipmentSlot;
import com.meedix.mnpc.api.skin.Skin;
import com.meedix.mnpc.api.trait.Trait;
import com.meedix.mnpc.core.NpcManagerImpl;
import com.meedix.mnpc.trait.HologramTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * YAML persistence split across two files:
 *
 * <ul>
 *   <li>{@code plugins/MNPC/npc.yml} — the NPCs themselves (location, skin,
 *       equipment and all traits except holograms);</li>
 *   <li>{@code plugins/MNPC/holograms.yml} — hologram lines, keyed by NPC
 *       name with a {@code line} list, so server owners can edit hologram
 *       text without touching NPC data.</li>
 * </ul>
 *
 * <p>Items are stored via Bukkit's native ItemStack serialization, traits via
 * {@link TraitRegistry}. A legacy {@code npcs.yml} is migrated to
 * {@code npc.yml} automatically on first load.</p>
 */
public final class YamlNpcStorage implements NpcStorage {

    private static final String FILE_NAME = "npc.yml";
    private static final String LEGACY_FILE_NAME = "npcs.yml";
    private static final String HOLOGRAMS_FILE_NAME = "holograms.yml";
    /** Key of the hologram lines list inside holograms.yml. */
    private static final String LINE_KEY = "line";

    private final Plugin plugin;
    private final TraitRegistry traitRegistry;
    private final File file;
    private final File hologramsFile;

    /**
     * @param plugin        owning plugin (data folder + logger)
     * @param traitRegistry registry used to (de)serialize traits
     */
    public YamlNpcStorage(Plugin plugin, TraitRegistry traitRegistry) {
        this.plugin = plugin;
        this.traitRegistry = traitRegistry;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        this.hologramsFile = new File(plugin.getDataFolder(), HOLOGRAMS_FILE_NAME);
        migrateLegacyFile();
    }

    @Override
    public int load(NpcManagerImpl manager) {
        if (!file.exists()) {
            return 0;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration holograms = hologramsFile.exists()
                ? YamlConfiguration.loadConfiguration(hologramsFile)
                : new YamlConfiguration();
        int loaded = 0;
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            try {
                loadNpc(manager, key, section, holograms);
                loaded++;
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Could not load NPC " + key, exception);
            }
        }
        return loaded;
    }

    @Override
    public void save(java.util.Collection<Npc> npcs) {
        YamlConfiguration yaml = new YamlConfiguration();
        YamlConfiguration holograms = new YamlConfiguration();
        for (Npc npc : npcs) {
            writeNpc(yaml.createSection(npc.getId().toString()), npc);
            npc.getTrait(HologramTrait.class).ifPresent(trait ->
                    holograms.set(npc.getName() + "." + LINE_KEY, trait.getLines()));
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                throw new IOException("Could not create " + plugin.getDataFolder());
            }
            yaml.save(file);
            holograms.save(hologramsFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save NPC storage", exception);
        }
    }

    /** Renames a legacy {@code npcs.yml} to {@code npc.yml} once. */
    private void migrateLegacyFile() {
        File legacy = new File(plugin.getDataFolder(), LEGACY_FILE_NAME);
        if (!file.exists() && legacy.exists()) {
            if (legacy.renameTo(file)) {
                plugin.getLogger().info("Migrated " + LEGACY_FILE_NAME + " to " + FILE_NAME);
            } else {
                plugin.getLogger().warning("Could not migrate " + LEGACY_FILE_NAME
                        + " to " + FILE_NAME + "; loading will be skipped");
            }
        }
    }

    private void loadNpc(NpcManagerImpl manager, String key, ConfigurationSection section,
                         YamlConfiguration holograms) {
        String worldName = section.getString("location.world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Skipping NPC " + key + ": world '" + worldName + "' is not loaded");
            return;
        }
        Location location = new Location(world,
                section.getDouble("location.x"),
                section.getDouble("location.y"),
                section.getDouble("location.z"),
                (float) section.getDouble("location.yaw"),
                (float) section.getDouble("location.pitch"));

        Skin skin = null;
        if (section.isString("skin.texture") && section.isString("skin.signature")) {
            skin = new Skin(section.getString("skin.texture"), section.getString("skin.signature"));
        }

        Npc npc = manager.restoreNpc(UUID.fromString(key),
                section.getString("name", "NPC"), location, skin,
                section.getDouble("view-radius", 48.0));
        npc.setNameVisible(section.getBoolean("name-visible", true));

        ConfigurationSection equipment = section.getConfigurationSection("equipment");
        if (equipment != null) {
            for (String slotName : equipment.getKeys(false)) {
                ItemStack item = equipment.getItemStack(slotName);
                if (item != null) {
                    npc.setEquipment(NpcEquipmentSlot.valueOf(slotName), item);
                }
            }
        }

        ConfigurationSection traits = section.getConfigurationSection("traits");
        if (traits != null) {
            for (String traitId : traits.getKeys(false)) {
                traitRegistry.byId(traitId).ifPresentOrElse(factory -> {
                    ConfigurationSection data = traits.getConfigurationSection(traitId);
                    Map<String, Object> values = data == null ? Map.of() : data.getValues(false);
                    npc.addTrait(factory.create(values));
                }, () -> plugin.getLogger().warning(
                        "Skipping unknown trait '" + traitId + "' of NPC " + key));
            }
        }

        loadHologram(npc, holograms);
    }

    /** Attaches the hologram stored in holograms.yml, if any. */
    private void loadHologram(Npc npc, YamlConfiguration holograms) {
        List<String> lines = holograms.getStringList(npc.getName() + "." + LINE_KEY);
        if (lines.isEmpty()) {
            // Legacy path: holograms previously lived inside the NPC's traits
            // section, in which case the trait is already attached.
            return;
        }
        npc.getTrait(HologramTrait.class).ifPresentOrElse(
                trait -> trait.setLines(lines),
                () -> npc.addTrait(new HologramTrait(lines)));
    }

    private void writeNpc(ConfigurationSection section, Npc npc) {
        section.set("name", npc.getName());
        Location location = npc.getLocation();
        section.set("location.world", location.getWorld().getName());
        section.set("location.x", location.getX());
        section.set("location.y", location.getY());
        section.set("location.z", location.getZ());
        section.set("location.yaw", (double) location.getYaw());
        section.set("location.pitch", (double) location.getPitch());
        section.set("view-radius", npc.getViewRadius());
        section.set("name-visible", npc.isNameVisible());
        npc.getSkin().ifPresent(skin -> {
            section.set("skin.texture", skin.texture());
            section.set("skin.signature", skin.signature());
        });
        for (Map.Entry<NpcEquipmentSlot, ItemStack> entry : npc.getEquipment().entrySet()) {
            section.set("equipment." + entry.getKey().name(), entry.getValue());
        }
        for (Trait trait : npc.getTraits()) {
            if (trait instanceof HologramTrait) {
                continue; // hologram lines live in holograms.yml
            }
            traitRegistry.byTrait(trait).ifPresent(factory -> {
                Map<String, Object> data = factory.serialize(trait);
                section.set("traits." + factory.id(), new HashMap<>(data));
            });
        }
    }
}
