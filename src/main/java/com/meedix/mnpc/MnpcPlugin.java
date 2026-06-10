package com.meedix.mnpc;

import com.meedix.mnpc.api.NpcManager;
import com.meedix.mnpc.api.trait.Trait;
import com.meedix.mnpc.command.MnpcCommand;
import com.meedix.mnpc.core.NpcManagerImpl;
import com.meedix.mnpc.core.NpcRegistry;
import com.meedix.mnpc.core.tick.NpcTickManager;
import com.meedix.mnpc.core.visibility.VisibilityService;
import com.meedix.mnpc.listener.NpcInteractListener;
import com.meedix.mnpc.listener.PlayerConnectionListener;
import com.meedix.mnpc.nms.PacketAdapter;
import com.meedix.mnpc.nms.PacketAdapterFactory;
import com.meedix.mnpc.skin.SkinService;
import com.meedix.mnpc.storage.TraitRegistry;
import com.meedix.mnpc.storage.YamlNpcStorage;
import com.meedix.mnpc.trait.FollowTrait;
import com.meedix.mnpc.trait.HologramTrait;
import com.meedix.mnpc.trait.LookAtPlayerTrait;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;

/**
 * MNPC — a high-performance, packet-based NPC engine for Paper 26.1.2.
 *
 * <p>Wires the engine together: packet adapter, registry, visibility,
 * central tick manager, skin service, YAML storage, listeners and the
 * {@code /mnpc} admin command. The public API is exposed both through
 * {@link #getNpcManager()} and the Bukkit services manager.</p>
 */
public final class MnpcPlugin extends JavaPlugin {

    private static MnpcPlugin instance;

    private PacketAdapter packetAdapter;
    private NpcRegistry registry;
    private VisibilityService visibility;
    private NpcTickManager tickManager;
    private NpcManagerImpl npcManager;
    private YamlNpcStorage storage;
    private TraitRegistry traitRegistry;
    private BukkitTask autosaveTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        packetAdapter = PacketAdapterFactory.create();
        registry = new NpcRegistry();
        visibility = new VisibilityService(this, packetAdapter);
        traitRegistry = new TraitRegistry();
        registerBuiltinTraits();
        storage = new YamlNpcStorage(this, traitRegistry);
        npcManager = new NpcManagerImpl(registry, visibility, packetAdapter,
                new SkinService(), storage,
                getConfig().getDouble("view-radius", 48.0));

        int loaded = storage.load(npcManager);
        getLogger().info("Loaded " + loaded + " NPC(s) from npcs.yml");

        tickManager = new NpcTickManager(this, registry, visibility);
        tickManager.start();

        NpcInteractListener interactListener = new NpcInteractListener(registry);
        Bukkit.getPluginManager().registerEvents(interactListener, this);
        Bukkit.getPluginManager().registerEvents(
                new PlayerConnectionListener(registry, visibility), this);

        MnpcCommand command = new MnpcCommand(this);
        getCommand("mnpc").setExecutor(command);
        getCommand("mnpc").setTabCompleter(command);

        Bukkit.getServicesManager().register(NpcManager.class, npcManager, this, ServicePriority.Normal);

        long autosaveTicks = getConfig().getLong("autosave-interval-seconds", 300) * 20L;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this,
                () -> npcManager.saveAll(), autosaveTicks, autosaveTicks);
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        if (tickManager != null) {
            tickManager.stop();
        }
        if (npcManager != null) {
            npcManager.saveAll();
            for (var npc : npcManager.getNpcs()) {
                npcManager.removeNpc(npc);
            }
        }
        if (visibility != null) {
            visibility.shutdown();
        }
        instance = null;
    }

    /** @return the running plugin instance. */
    public static MnpcPlugin getInstance() {
        return instance;
    }

    /** @return the public NPC API. */
    public NpcManager getNpcManager() {
        return npcManager;
    }

    /** @return the version-specific packet adapter. */
    public PacketAdapter getPacketAdapter() {
        return packetAdapter;
    }

    /** @return the trait persistence registry. */
    public TraitRegistry getTraitRegistry() {
        return traitRegistry;
    }

    private void registerBuiltinTraits() {
        traitRegistry.register(new TraitRegistry.TraitFactory() {
            @Override public String id() {
                return "look_at_player";
            }

            @Override public Class<? extends Trait> type() {
                return LookAtPlayerTrait.class;
            }

            @Override public Trait create(Map<String, Object> data) {
                double range = data.get("range") instanceof Number number
                        ? number.doubleValue() : LookAtPlayerTrait.DEFAULT_RANGE;
                return new LookAtPlayerTrait(range);
            }

            @Override public Map<String, Object> serialize(Trait trait) {
                return Map.of("range", ((LookAtPlayerTrait) trait).getRange());
            }
        });

        traitRegistry.register(new TraitRegistry.TraitFactory() {
            @Override public String id() {
                return "hologram";
            }

            @Override public Class<? extends Trait> type() {
                return HologramTrait.class;
            }

            @Override public Trait create(Map<String, Object> data) {
                List<String> lines = data.get("lines") instanceof List<?> raw
                        ? raw.stream().map(String::valueOf).toList() : List.of();
                return new HologramTrait(packetAdapter, lines);
            }

            @Override public Map<String, Object> serialize(Trait trait) {
                return Map.of("lines", ((HologramTrait) trait).getLines());
            }
        });

        traitRegistry.register(new TraitRegistry.TraitFactory() {
            @Override public String id() {
                return "follow";
            }

            @Override public Class<? extends Trait> type() {
                return FollowTrait.class;
            }

            @Override public Trait create(Map<String, Object> data) {
                return new FollowTrait();
            }

            @Override public Map<String, Object> serialize(Trait trait) {
                return Map.of();
            }
        });
    }
}
