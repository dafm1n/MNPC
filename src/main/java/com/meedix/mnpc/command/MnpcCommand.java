package com.meedix.mnpc.command;

import com.meedix.mnpc.MnpcPlugin;
import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.NpcAnimation;
import com.meedix.mnpc.api.NpcEquipmentSlot;
import com.meedix.mnpc.api.NpcManager;
import com.meedix.mnpc.trait.FollowTrait;
import com.meedix.mnpc.trait.HologramTrait;
import com.meedix.mnpc.trait.LookAtPlayerTrait;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The {@code /mnpc} admin command — also serves as a living example of the
 * MNPC API usage.
 */
public final class MnpcCommand implements org.bukkit.command.CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "remove", "skin", "tphere", "lookat", "anim", "equiphand",
            "hologram", "follow", "unfollow", "list");

    private final MnpcPlugin plugin;

    /** @param plugin the running plugin instance */
    public MnpcCommand(MnpcPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("/mnpc <" + String.join("|", SUBCOMMANDS) + ">",
                    NamedTextColor.YELLOW));
            return true;
        }
        NpcManager manager = plugin.getNpcManager();
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("list")) {
            player.sendMessage(Component.text("NPCs: " + manager.getNpcs().size(), NamedTextColor.GOLD));
            for (Npc npc : manager.getNpcs()) {
                player.sendMessage(Component.text(" - " + npc.getName()
                        + " (viewers: " + npc.getViewers().size() + ")", NamedTextColor.GRAY));
            }
            return true;
        }
        if (sub.equals("create")) {
            if (args.length < 2) {
                return usage(player, "create <name>");
            }
            Npc npc = manager.createNpc(args[1], player.getLocation());
            player.sendMessage(Component.text("Created NPC " + npc.getName(), NamedTextColor.GREEN));
            return true;
        }

        // All remaining subcommands target an existing NPC.
        if (args.length < 2) {
            return usage(player, sub + " <name> ...");
        }
        Npc npc = manager.getNpc(args[1]).orElse(null);
        if (npc == null) {
            player.sendMessage(Component.text("No NPC named '" + args[1] + "'", NamedTextColor.RED));
            return true;
        }

        switch (sub) {
            case "remove" -> {
                manager.removeNpc(npc);
                player.sendMessage(Component.text("Removed " + npc.getName(), NamedTextColor.GREEN));
            }
            case "skin" -> {
                if (args.length < 3) {
                    return usage(player, "skin <name> <minecraftAccount>");
                }
                manager.fetchSkin(args[2]).whenComplete((skin, error) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (error != null) {
                                player.sendMessage(Component.text(
                                        "Skin fetch failed: " + error.getMessage(), NamedTextColor.RED));
                            } else {
                                npc.setSkin(skin);
                                player.sendMessage(Component.text("Skin applied.", NamedTextColor.GREEN));
                            }
                        }));
            }
            case "tphere" -> {
                npc.teleport(player.getLocation());
                player.sendMessage(Component.text("Teleported " + npc.getName(), NamedTextColor.GREEN));
            }
            case "lookat" -> npc.lookAt(player);
            case "anim" -> {
                if (args.length < 3) {
                    return usage(player, "anim <name> <" + names(NpcAnimation.values()) + ">");
                }
                try {
                    npc.playAnimation(NpcAnimation.valueOf(args[2].toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException invalid) {
                    return usage(player, "anim <name> <" + names(NpcAnimation.values()) + ">");
                }
            }
            case "equiphand" -> {
                npc.setEquipment(NpcEquipmentSlot.MAIN_HAND, player.getInventory().getItemInMainHand());
                player.sendMessage(Component.text("Equipment updated.", NamedTextColor.GREEN));
            }
            case "hologram" -> {
                if (args.length < 3) {
                    return usage(player, "hologram <name> <text...> (use | for line breaks)");
                }
                String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                List<String> lines = Arrays.asList(text.split("\\|"));
                npc.getTrait(HologramTrait.class).ifPresentOrElse(
                        trait -> trait.setLines(lines),
                        () -> npc.addTrait(new HologramTrait(plugin.getPacketAdapter(), lines)));
                if (npc.getTrait(LookAtPlayerTrait.class).isEmpty()) {
                    npc.addTrait(new LookAtPlayerTrait());
                }
            }
            case "follow" -> {
                FollowTrait trait = npc.getTrait(FollowTrait.class).orElseGet(() -> {
                    FollowTrait created = new FollowTrait();
                    npc.addTrait(created);
                    return created;
                });
                trait.setTarget(player);
                player.sendMessage(Component.text(npc.getName() + " is now following you.",
                        NamedTextColor.GREEN));
            }
            case "unfollow" -> npc.removeTrait(FollowTrait.class);
            default -> player.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("create")) {
            return plugin.getNpcManager().getNpcs().stream().map(Npc::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT)
                            .startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    private boolean usage(Player player, String usage) {
        player.sendMessage(Component.text("Usage: /mnpc " + usage, NamedTextColor.YELLOW));
        return true;
    }

    private static String names(Enum<?>[] values) {
        return String.join("|", Arrays.stream(values).map(Enum::name).toList());
    }
}
