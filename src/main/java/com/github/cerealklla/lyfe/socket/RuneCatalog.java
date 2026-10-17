package com.github.cerealklla.lyfe.socket;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Each of the 30 {@link RuneId}s' one fixed, "very minor" universal benefit (explicit user spec --
 * "no quality or variability between weapon vs tool vs armor"). Same reasoning as {@link GemCatalog}:
 * every entry maps onto a real vanilla {@link Attribute} so {@link SocketApplier} is the one shared
 * application mechanism for gems and runes alike. Magnitudes are deliberately smaller than a single
 * Chipped-quality gem step, per "very minor."
 */
public final class RuneCatalog {

    public record RuneStat(Holder<Attribute> attribute, double amount, String shortLabel) {
    }

    private static final Map<RuneId, RuneStat> TABLE = new EnumMap<>(RuneId.class);

    static {
        put(RuneId.ZAR, Attributes.ATTACK_DAMAGE, 0.5, "Attack Damage");
        put(RuneId.ITH, Attributes.ARMOR, 0.5, "Armor");
        put(RuneId.VEX, Attributes.ATTACK_SPEED, 0.03, "Attack Speed");
        put(RuneId.LORN, Attributes.MAX_HEALTH, 1.0, "Max Health (x0.5 heart)");
        put(RuneId.KEL, Attributes.MOVEMENT_SPEED, 0.005, "Movement Speed");
        put(RuneId.THAL, Attributes.ARMOR_TOUGHNESS, 0.5, "Armor Toughness");
        put(RuneId.ORIN, Attributes.KNOCKBACK_RESISTANCE, 0.03, "Knockback Resistance");
        put(RuneId.MYR, Attributes.LUCK, 0.5, "Luck");
        put(RuneId.FEN, Attributes.LUCK, 0.3, "Luck");
        put(RuneId.DASK, Attributes.MINING_EFFICIENCY, 0.4, "Mining Efficiency");
        put(RuneId.WYN, Attributes.ATTACK_KNOCKBACK, 0.15, "Attack Knockback");
        put(RuneId.HALE, Attributes.MAX_ABSORPTION, 1.0, "Max Absorption");
        put(RuneId.BROM, Attributes.ARMOR, 0.3, "Armor");
        put(RuneId.GLEN, Attributes.FALL_DAMAGE_MULTIPLIER, -0.08, "Fall Damage Reduction");
        put(RuneId.KORR, Attributes.SWEEPING_DAMAGE_RATIO, 0.06, "Sweeping Damage");
        put(RuneId.SAEL, Attributes.BLOCK_BREAK_SPEED, 0.3, "Block Break Speed");
        put(RuneId.NYM, Attributes.JUMP_STRENGTH, 0.015, "Jump Strength");
        put(RuneId.PYRE, Attributes.ATTACK_DAMAGE, 0.3, "Attack Damage");
        put(RuneId.DOTH, Attributes.MAX_HEALTH, 0.5, "Max Health");
        put(RuneId.QUIN, Attributes.OXYGEN_BONUS, 0.08, "Oxygen Bonus");
        put(RuneId.RASK, Attributes.ATTACK_KNOCKBACK, 0.1, "Attack Knockback");
        put(RuneId.ESH, Attributes.SUBMERGED_MINING_SPEED, 0.3, "Underwater Mining Speed");
        put(RuneId.VORN, Attributes.MAX_ABSORPTION, 0.5, "Max Absorption");
        put(RuneId.TILN, Attributes.ARMOR_TOUGHNESS, 0.3, "Armor Toughness");
        put(RuneId.ASHK, Attributes.SWEEPING_DAMAGE_RATIO, 0.04, "Sweeping Damage");
        put(RuneId.OLM, Attributes.SAFE_FALL_DISTANCE, 0.5, "Safe Fall Distance");
        put(RuneId.DREV, Attributes.KNOCKBACK_RESISTANCE, 0.02, "Knockback Resistance");
        put(RuneId.SORN, Attributes.LUCK, 0.4, "Luck");
        put(RuneId.KAEL, Attributes.STEP_HEIGHT, 0.08, "Step Height");
        put(RuneId.RYTH, Attributes.ATTACK_SPEED, 0.02, "Attack Speed");
    }

    private static void put(RuneId id, Holder<Attribute> attribute, double amount, String label) {
        TABLE.put(id, new RuneStat(attribute, amount, label));
    }

    private RuneCatalog() {
    }

    public static RuneStat of(RuneId id) {
        return TABLE.get(id);
    }
}
