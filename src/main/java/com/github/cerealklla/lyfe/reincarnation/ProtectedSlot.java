package com.github.cerealklla.lyfe.reincarnation;

import net.minecraft.world.entity.EquipmentSlot;

/**
 * One inventory/equipment slot Reincarnation can protect from death, in **unlock order** — level
 * N protects every slot from {@code values()[0]} through {@code values()[N-1]} (see
 * {@link ReincarnationSlots#protectedSlotsForLevel}). Declaration order here *is* the unlock
 * order, per the user's own spec plus Offhand inserted at level 9 (right after Boots, before the
 * hotbar progression resumes) — this plan's own placement call, since the user didn't specify one.
 */
public enum ProtectedSlot {
    HOTBAR_0,
    ARMOR_CHEST,
    HOTBAR_1,
    ARMOR_LEGS,
    HOTBAR_2,
    ARMOR_HEAD,
    HOTBAR_3,
    ARMOR_FEET,
    OFFHAND,
    HOTBAR_4,
    HOTBAR_5,
    HOTBAR_6,
    HOTBAR_7,
    HOTBAR_8;

    /** Armor pieces and Offhand are never type-filtered (nothing else can occupy them); only a hotbar slot is. */
    public boolean isTypeFiltered() {
        return !isEquipment();
    }

    public boolean isEquipment() {
        return this != HOTBAR_0 && this != HOTBAR_1 && this != HOTBAR_2 && this != HOTBAR_3
                && this != HOTBAR_4 && this != HOTBAR_5 && this != HOTBAR_6 && this != HOTBAR_7 && this != HOTBAR_8;
    }

    public EquipmentSlot equipmentSlot() {
        return switch (this) {
            case ARMOR_HEAD -> EquipmentSlot.HEAD;
            case ARMOR_CHEST -> EquipmentSlot.CHEST;
            case ARMOR_LEGS -> EquipmentSlot.LEGS;
            case ARMOR_FEET -> EquipmentSlot.FEET;
            case OFFHAND -> EquipmentSlot.OFFHAND;
            default -> throw new IllegalStateException(this + " is not an equipment slot");
        };
    }

    public int hotbarIndex() {
        return switch (this) {
            case HOTBAR_0 -> 0;
            case HOTBAR_1 -> 1;
            case HOTBAR_2 -> 2;
            case HOTBAR_3 -> 3;
            case HOTBAR_4 -> 4;
            case HOTBAR_5 -> 5;
            case HOTBAR_6 -> 6;
            case HOTBAR_7 -> 7;
            case HOTBAR_8 -> 8;
            default -> throw new IllegalStateException(this + " is not a hotbar slot");
        };
    }
}
