package com.meedix.mnpc.trait;

import com.meedix.mnpc.MnpcPlugin;
import com.meedix.mnpc.api.trait.Trait;
import com.meedix.mnpc.nms.PacketAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Periodically toggles the NPC's pose between standing and sneaking (squat)
 * every second. The sneak bit (0x02) in the entity flags byte is used so the
 * client renders the crouching animation.
 */
public final class SquatTrait extends Trait {

    private static final int DATA_SHARED_FLAGS = 0;
    private static final byte SNEAK_BIT = 0x02;

    private final PacketAdapter adapter;
    private int tickCounter;
    private boolean sneaking;
    private int taskId = -1;

    public SquatTrait() {
        this.adapter = MnpcPlugin.getInstance().getPacketAdapter();
    }

    @Override
    public void onSpawn(Player viewer) {
        if (taskId == -1) {
            startTask();
        }
    }

    @Override
    public void onDespawn(Player viewer) {
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onRemove() {
        stopTask();
        if (getNpc() != null) {
            adapter.sendEntityMetadata(
                    getNpc().getViewers(), getNpc().getEntityId(),
                    DATA_SHARED_FLAGS, (byte) 0);
        }
    }

    private void startTask() {
        taskId = Bukkit.getScheduler().runTaskTimer(
                MnpcPlugin.getInstance(), this::tickSquat, 0L, 10L).getTaskId();
    }

    private void stopTask() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void tickSquat() {
        if (getNpc() == null || getNpc().isRemoved()) {
            stopTask();
            return;
        }
        sneaking = !sneaking;
        byte flags = sneaking ? SNEAK_BIT : 0;
        adapter.sendEntityMetadata(
                getNpc().getViewers(), getNpc().getEntityId(),
                DATA_SHARED_FLAGS, flags);
    }
}
