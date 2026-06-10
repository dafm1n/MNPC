package com.meedix.mnpc.api.event;

/**
 * The kind of click a player performed on an NPC.
 */
public enum NpcClickType {

    /** Attack (left click). */
    LEFT_CLICK,
    /** Interact (right click). */
    RIGHT_CLICK,
    /** Attack while sneaking. */
    SHIFT_LEFT_CLICK,
    /** Interact while sneaking. */
    SHIFT_RIGHT_CLICK;

    /**
     * Resolves the click type from raw interaction data.
     *
     * @param attack   whether the interaction was an attack
     * @param sneaking whether the player was sneaking
     * @return the matching click type
     */
    public static NpcClickType of(boolean attack, boolean sneaking) {
        if (attack) {
            return sneaking ? SHIFT_LEFT_CLICK : LEFT_CLICK;
        }
        return sneaking ? SHIFT_RIGHT_CLICK : RIGHT_CLICK;
    }
}
