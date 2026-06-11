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
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
            "hologram", "follow", "unfollow", "togglename", "list", "help");

    /** Accent color used in the help screen (light violet). */
    private static final TextColor ACCENT = TextColor.color(0x9D7AFF);
    /** Help screen entries: {arguments, description}. */
    private static final String[][] HELP = {
            {"create <имя>", "создать НПС на твоей позиции"},
            {"remove <имя>", "удалить НПС"},
            {"skin <имя> <ник>", "применить скин аккаунта Minecraft"},
            {"tphere <имя>", "телепортировать НПС к себе"},
            {"lookat <имя>", "НПС посмотрит на тебя"},
            {"anim <имя> <тип>", "проиграть анимацию (SWING_MAIN_ARM…)"},
            {"equiphand <имя>", "дать НПС предмет из твоей руки"},
            {"hologram <имя> <текст>", "голограмма над головой (| — перенос)"},
            {"follow <имя>", "НПС следует за тобой"},
            {"unfollow <имя>", "перестать следовать"},
            {"togglename <имя>", "показать/скрыть имя над головой"},
            {"list", "список всех НПС"},
            {"help", "это меню"},
    };

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
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(player, label);
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
            case "togglename" -> {
                boolean visible = !npc.isNameVisible();
                npc.setNameVisible(visible);
                player.sendMessage(Component.text(visible
                                ? "Имя НПС " + npc.getName() + " снова видно."
                                : "Имя НПС " + npc.getName() + " скрыто.",
                        visible ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            }
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

    /**
     * Sends the formatted, clickable command overview. Every row inserts the
     * command into the player's chat input on click.
     *
     * @param player the player to message
     * @param label  the alias the command was executed with ({@code npc}/{@code mnpc})
     */
    private void sendHelp(Player player, String label) {
        Component divider = Component.text("                                                  ",
                NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH);
        player.sendMessage(divider);
        player.sendMessage(Component.text("  ✦ ", ACCENT)
                .append(Component.text("MNPC", ACCENT, TextDecoration.BOLD))
                .append(Component.text("  команды НПС-движка", NamedTextColor.GRAY)));
        player.sendMessage(Component.empty());
        for (String[] entry : HELP) {
            String base = "/" + label + " " + entry[0].split(" ")[0];
            player.sendMessage(Component.text("  ▪ ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("/" + label + " ", ACCENT)
                            .append(Component.text(entry[0], NamedTextColor.WHITE)))
                    .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(entry[1], NamedTextColor.GRAY))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Нажми, чтобы вставить ", NamedTextColor.GRAY)
                                    .append(Component.text(base, ACCENT))))
                    .clickEvent(ClickEvent.suggestCommand(base + " ")));
        }
        player.sendMessage(Component.empty());
        player.sendMessage(divider);
    }

    private boolean usage(Player player, String usage) {
        player.sendMessage(Component.text("Usage: /mnpc " + usage, NamedTextColor.YELLOW));
        return true;
    }

    private static String names(Enum<?>[] values) {
        return String.join("|", Arrays.stream(values).map(Enum::name).toList());
    }
}
