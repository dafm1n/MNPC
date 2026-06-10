package com.meedix.mnpc.api;

/**
 * One-shot entity animations that an NPC can play for its viewers.
 */
public enum NpcAnimation {

    /** Swing the main arm. */
    SWING_MAIN_ARM,
    /** Swing the off hand. */
    SWING_OFF_HAND,
    /** Red hurt flash + hurt sound direction. */
    HURT,
    /** Critical hit particles. */
    CRITICAL_HIT,
    /** Enchanted (magic) critical hit particles. */
    MAGIC_CRITICAL_HIT
}
