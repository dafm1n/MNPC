package com.meedix.mnpc.trait;

import com.meedix.mnpc.api.Npc;
import com.meedix.mnpc.api.event.NpcClickType;
import com.meedix.mnpc.api.event.NpcInteractEvent;
import com.meedix.mnpc.api.trait.Trait;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Executes a configurable server command when a player clicks the NPC.
 * The placeholder {@code %player%} is replaced with the clicking player's name.
 */
public final class ClickCommandTrait extends Trait {

    private final String command;

    /**
     * @param command the command to execute (use {@code %player%} as placeholder)
     */
    public ClickCommandTrait(String command) {
        this.command = command;
    }

    /** @return the command template. */
    public String getCommand() {
        return command;
    }

    @Override
    public void onSpawn(Player viewer) {
    }

    @Override
    public void onDespawn(Player viewer) {
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onRemove() {
    }

    /**
     * Called externally when the NPC carrying this trait is clicked. Dispatches
     * the command as the clicking player.
     *
     * @param event the interact event
     */
    public void onClick(NpcInteractEvent event) {
        Player player = event.getPlayer();
        String cmd = command.replace("%player%", player.getName());
        Bukkit.dispatchCommand(player, cmd);
    }
}
