package com.meedix.mnpc.api;

/**
 * Equipment slots supported by NPCs. Mirrors the vanilla humanoid slots
 * while keeping the public API free of NMS types.
 */
public enum NpcEquipmentSlot {

    /** Head slot. */
    HELMET,
    /** Chest slot. */
    CHESTPLATE,
    /** Legs slot. */
    LEGGINGS,
    /** Feet slot. */
    BOOTS,
    /** Main hand. */
    MAIN_HAND,
    /** Off hand. */
    OFF_HAND
}
