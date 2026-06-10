package com.meedix.mnpc.nms;

import com.meedix.mnpc.nms.v26_1_2.PacketAdapterImpl;
import org.bukkit.Bukkit;

/**
 * Resolves the {@link PacketAdapter} implementation matching the running
 * server version. New versions are added here.
 */
public final class PacketAdapterFactory {

    private PacketAdapterFactory() {
    }

    /**
     * @return the adapter for the running server
     * @throws IllegalStateException if the server version is unsupported
     */
    public static PacketAdapter create() {
        String minecraftVersion = Bukkit.getMinecraftVersion();
        if (minecraftVersion.equals("26.1.2")) {
            return new PacketAdapterImpl();
        }
        throw new IllegalStateException(
                "MNPC does not support Minecraft " + minecraftVersion + " (supported: 26.1.2)");
    }
}
