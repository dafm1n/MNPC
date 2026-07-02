package com.meedix.mnpc.trait;

import com.meedix.mnpc.api.trait.Trait;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Executes a command <em>as the clicking player</em> when the NPC is
 * right-clicked. One command per NPC; setting a new command overwrites the
 * previous one.
 *
 * <p>The placeholder {@code %player%} is replaced by the clicking player's
 * name before execution. Because the command runs through
 * {@link Player#performCommand(String)}, the player's own permissions apply
 * — no privilege escalation is possible.</p>
 *
 * <pre>{@code
 * npc.addTrait(new CommandTrait("warp spawn"));
 * }</pre>
 */
public final class CommandTrait extends Trait {

    private volatile String command;

    /**
     * @param command the command to run on click, with or without a leading
     *                slash; supports the {@code %player%} placeholder
     */
    public CommandTrait(String command) {
        this.command = normalize(command);
    }

    /** @return the configured command (without a leading slash). */
    public String getCommand() {
        return command;
    }

    /**
     * Replaces the configured command.
     *
     * @param command the new command, with or without a leading slash
     */
    public void setCommand(String command) {
        this.command = normalize(command);
    }

    /**
     * Runs the configured command as the given player.
     *
     * @param player the player executing the command
     */
    public void execute(Player player) {
        player.performCommand(command.replace("%player%", player.getName()));
    }

    private static String normalize(String command) {
        Objects.requireNonNull(command, "command");
        String trimmed = command.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Command must not be empty");
        }
        return trimmed;
    }
}
